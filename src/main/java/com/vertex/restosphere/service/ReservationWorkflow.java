package com.vertex.restosphere.service;

import static com.vertex.restosphere.service.WorkspaceStore.*;
import com.vertex.restosphere.entity.*;
import com.vertex.restosphere.common.enums.*;
import com.vertex.restosphere.exception.ApiException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ReservationWorkflow {
    private final WorkspaceStore db;
    private final ServiceWorkflow service;
    private final ReservationMailer mailer;
    static final Map<String,String> HISTORY_LABELS=Map.of("edit","Cập nhật lịch đặt","assign","Gán / đổi bàn","reservation.move","Quản lý đổi bàn","confirm","Xác nhận","reject","Từ chối","cancel","Hủy lịch","no-show","Không đến","check-in","Check-in","handover","Bàn giao cho phục vụ");
    static String phone(Map<String,Object> input) {return required(input,"phone",30).replaceAll("[\\s().-]", "");}
    public static String text(Map<String,Object> input, String key) { return Objects.toString(input.get(key), "").trim(); }
    public static String required(Map<String,Object> input, String key, int max) {
        String value = text(input,key);
        if(value.isBlank() || value.length()>max) throw invalid(key + " is required (max " + max + ")");
        return value;
    }
    public static BigDecimal number(Map<String,Object> input,String key) {
        try { return new BigDecimal(input.get(key).toString()); } catch(Exception e) { throw invalid("Invalid " + key); }
    }
    public static int integer(Map<String,Object> input,String key,int min,int max) {
        try { int n=number(input,key).intValueExact(); if(n<min||n>max) throw invalid("Invalid " + key); return n; }
        catch(ArithmeticException e) { throw invalid("Invalid " + key); }
    }
    @SuppressWarnings("unchecked") public static Map<String,Object> object(Map<String,Object> input,String key) {
        if(!(input.get(key) instanceof Map)) throw invalid("Missing " + key); return (Map<String,Object>)input.get(key);
    }
    @SuppressWarnings("unchecked") public static List<Map<String,Object>> lines(Map<String,Object> input,String key) {
        Object value=input.get(key); if(value==null) return List.of();
        if(!(value instanceof List<?> list) || list.size()>100 || list.stream().anyMatch(x->!(x instanceof Map))) throw invalid("Invalid " + key);
        return (List<Map<String,Object>>)value;
    }
    static final int EARLY_CHECK_IN_MINUTES=60;
    static final java.util.regex.Pattern HOURS=java.util.regex.Pattern.compile("(\\d{1,2})[:h](\\d{2})\\s*[-–]\\s*(\\d{1,2})[:h](\\d{2})");
    // Parses "10:00 - 22:00" from the admin opening-hours text; null = no limit. Overnight close gets +1440.
    public static int[] openingMinutes(String text) {
        var m=HOURS.matcher(text==null?"":text); if(!m.find()) return null;
        int open=Integer.parseInt(m.group(1))*60+Integer.parseInt(m.group(2)), close=Integer.parseInt(m.group(3))*60+Integer.parseInt(m.group(4));
        if(open>=1440||close>1440) return null;
        return new int[]{open,close<=open?close+1440:close};
    }
    public Map<String,Object> settings() {
        var s=db.lock(); var hours=openingMinutes(s.getOpeningHours());
        return map("openMinutes",hours==null?null:hours[0],"closeMinutes",hours==null?null:hours[1],"earlyCheckInMinutes",EARLY_CHECK_IN_MINUTES,"onlineReservations",s.isOnlineReservations(),"bufferMinutes",s.getBufferMinutes(),"noShowMinutes",s.getNoShowMinutes(),"tableHoldMinutes",s.getHoldMinutes(),"timezone",s.getTimezone(),"areas",db.all(Area.class).stream().map(a->map("id",id(a),"name",a.getName())).toList());
    }
    public Map<String,Object> createPublic(Map<String,Object> input) {
        var s=db.lock(); if(!s.isOnlineReservations()) throw ApiException.conflict("Online reservations are disabled");
        var r=create(input,null,true,false); s.setRevision(s.getRevision()+1);
        service.notify(Role.RECEPTIONIST,"NEW_WEB_RESERVATION","Đặt bàn mới từ web",r.getCode()+" · "+r.getCustomerName(),id(r));
        mailer.send(r,"Đã nhận yêu cầu đặt bàn","Nhà hàng đã nhận yêu cầu đặt bàn của bạn và sẽ xác nhận sớm.");
        db.flush(); return publicView(r);
    }
    public Reservation create(Map<String,Object> input,User actor,boolean web,boolean walkIn) {
        var r=new Reservation();
        r.setSource(web?Reservation.Source.WEB:walkIn?Reservation.Source.WALK_IN:Reservation.Source.PHONE);
        fill(r,input,web,walkIn); r.setCode(newCode(Instant.now())); db.save(r); history(r,actor,"CREATED",walkIn?"Khách trực tiếp đã đến":web?"Khách đặt bàn qua web":"Tạo lịch đặt");
        String tableId=text(input,"tableId");
        if(walkIn&&tableId.isEmpty()) throw invalid("tableId is required for walk-in");
        if(!web&&!tableId.isEmpty()) assign(r,tableId);
        if(walkIn) {
            var table=assignment(r).getTable();
            if(table.getStatus()!=TableStatus.AVAILABLE||db.all(TableSession.class).stream().anyMatch(s->s.getTable().equals(table)&&s.getStatus()!=TableSession.Status.CLOSED)) throw ApiException.conflict("Table is not available");
            r.setStatus(Reservation.Status.ARRIVED); r.setActualGuests(r.getGuestCount()); r.setArrivedAt(Instant.now());
        }
        replaceItems(r,input,web); return r;
    }
    // RS-ddMMyy-NNN: booking creation date (restaurant timezone) + sequence; never changes, even if the booking is moved.
    String newCode(Instant createdAt) {
        String prefix="RS-"+createdAt.atZone(ZoneId.of(db.lock().getTimezone())).format(java.time.format.DateTimeFormatter.ofPattern("ddMMyy"))+"-";
        int next=db.all(Reservation.class).stream().map(Reservation::getCode).filter(c->c.startsWith(prefix))
                .mapToInt(c->{try{return Integer.parseInt(c.substring(prefix.length()));}catch(NumberFormatException e){return 0;}}).max().orElse(0)+1;
        return prefix+String.format("%03d",next);
    }
    void fill(Reservation r,Map<String,Object> input,boolean web,boolean walkIn) {
        var customer=web?input:object(input,"customer");
        String name=text(customer,"name"); if(name.isEmpty()&&walkIn) name="Khách trực tiếp";
        if(name.isEmpty()||name.length()>150) throw invalid("name is required (max 150)"); r.setCustomerName(name);
        String phone=phone(customer);
        if(!phone.matches("\\+?[0-9]{8,15}")) throw invalid("Invalid phone"); r.setPhone(phone);
        String email=text(input,"email"); if(!email.isEmpty() && (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")||email.length()>254)) throw invalid("Invalid email"); r.setEmail(email);
        r.setGuestCount(integer(input,"guests",1,200));
        String areaId=required(input,"areaId",100); r.setArea(db.get(Area.class,areaId));
        Instant start;
        try { start=text(input,"startsAt").isEmpty()? LocalDateTime.parse(required(input,"date",10)+"T"+required(input,"time",5)).atZone(ZoneId.of(db.lock().getTimezone())).toInstant():Instant.parse(text(input,"startsAt")); }
        catch(DateTimeException e) { throw invalid("Invalid reservation date/time"); }
        boolean moved=r.getStartsAt()==null||!start.equals(r.getStartsAt());
        if(moved&&start.isBefore(Instant.now().minusSeconds(walkIn?1800:60)) || start.isAfter(Instant.now().plus(Duration.ofDays(365)))) throw invalid("Reservation date is outside booking window");
        int duration=input.containsKey(web?"durationMinutes":"duration")?integer(input,web?"durationMinutes":"duration",15,720):120;
        var hours=openingMinutes(db.lock().getOpeningHours());
        if(hours!=null) {
            var local=start.atZone(ZoneId.of(db.lock().getTimezone())).toLocalTime(); int m=local.getHour()*60+local.getMinute();
            if(m<hours[0]&&hours[1]>1440) m+=1440;
            if(m<hours[0]||(walkIn?m+1:m+duration)>hours[1]) throw invalid("Reservation must be within opening hours "+db.lock().getOpeningHours());
        }
        r.setStartsAt(start); r.setEndsAt(start.plusSeconds(duration*60L));
        String note=text(input,web?"note":"notes"); if(note.length()>1000) throw invalid("Note too long"); r.setNote(note);
        String occasion=text(input,"occasion"), special=text(input,"specialRequests");
        if(occasion.length()>150||special.length()>255) throw invalid("occasion/specialRequests too long");
        r.setOccasion(occasion); r.setSpecialRequests(special);
        int capacity=db.all(RestaurantTable.class).stream().filter(t->id(t.getArea()).equals(id(r.getArea()))&&t.getStatus()!=TableStatus.OUT_OF_SERVICE).mapToInt(RestaurantTable::getCapacity).sum();
        // Sums guests per area; does not check how they fit into individual tables.
        long buffer=db.lock().getBufferMinutes()*60L;
        int booked=db.all(Reservation.class).stream().filter(o->!o.equals(r)&&active(o)&&id(o.getArea()).equals(id(r.getArea()))&&o.getStartsAt().isBefore(r.getEndsAt().plusSeconds(buffer))&&o.getEndsAt().plusSeconds(buffer).isAfter(r.getStartsAt())).mapToInt(Reservation::getGuestCount).sum();
        if(capacity-booked<r.getGuestCount()) throw ApiException.conflict("Area has insufficient capacity for this time");
    }
    void replaceItems(Reservation r,Map<String,Object> input,boolean web) {
        db.all(ReservationItem.class).stream().filter(i->i.getReservation().equals(r)).forEach(db::remove);
        for(var line:lines(input,web?"items":"preorder")) {
            var menu=db.get(MenuItem.class,required(line,web?"dishId":"menuItemId",100));
            if(menu.getStatus()!=MenuItemStatus.AVAILABLE||!menu.getCategory().isVisible()) throw ApiException.conflict("Preorder item unavailable");
            var quantity=number(line,"quantity"); validateQuantity(menu,quantity);
            var item=new ReservationItem(); item.setReservation(r); item.setMenuItem(menu); item.setQuantity(quantity); item.setUnitPrice(menu.getPrice());
            if(text(line,"note").length()>1000) throw invalid("Note too long"); item.setNote(text(line,"note")); db.save(item);
        }
    }
    public static void validateQuantity(MenuItem item,BigDecimal quantity) {
        if(quantity.signum()<=0||quantity.compareTo(new BigDecimal("999"))>0||quantity.stripTrailingZeros().scale()>3||(!"kg".equals(item.getPriceUnit())&&quantity.stripTrailingZeros().scale()>0)) throw invalid("Invalid quantity for item unit");
    }
    public Map<String,Object> lookup(Map<String,Object> input) { return publicView(find(input)); }
    public Map<String,Object> cancelPublic(Map<String,Object> input) {
        var r=find(input); command("cancel",map("id",id(r),"reason",required(input,"reason",1000)),null);
        var s=db.lock(); s.setRevision(s.getRevision()+1); db.flush(); return publicView(r);
    }
    Reservation find(Map<String,Object> input) {
        db.lock(); String code=required(input,"code",30), phone=phone(input);
        return db.all(Reservation.class).stream().filter(r->r.getCode().equalsIgnoreCase(code)&&r.getPhone().equals(phone)).findFirst().orElseThrow(()->ApiException.notFound("Reservation"));
    }
    public int expireNoShows() {
        var s=db.lock(); var deadline=Instant.now().minusSeconds(s.getNoShowMinutes()*60L);
        var late=db.all(Reservation.class).stream().filter(r->r.getStatus()==Reservation.Status.CONFIRMED&&!r.getStartsAt().isAfter(deadline)).toList();
        for(var r:late) {command("no-show",map("id",id(r)),null);service.notify(Role.RECEPTIONIST,"AUTO_NO_SHOW","Tự động đánh dấu không đến",r.getCode()+" · "+r.getCustomerName(),id(r));}
        if(!late.isEmpty()) s.setRevision(s.getRevision()+1);
        return late.size();
    }
    public void assign(Reservation r,String tableId) {
        var table=db.get(RestaurantTable.class,tableId);
        int guests=r.getActualGuests()==null?r.getGuestCount():r.getActualGuests();
        if(!id(table.getArea()).equals(id(r.getArea()))||table.getCapacity()<guests||table.getStatus()==TableStatus.OUT_OF_SERVICE) throw ApiException.conflict("Table does not match reservation");
        ensureFree(r,table,r.getStartsAt(),r.getEndsAt());
        db.all(ReservationTable.class).stream().filter(a->a.getReservation().equals(r)).forEach(db::remove); db.flush();
        var a=new ReservationTable();a.setReservation(r);a.setTable(table);db.save(a);
    }
    void ensureFree(Reservation r,RestaurantTable table,Instant start,Instant end) {
        long buffer=db.lock().getBufferMinutes()*60L;
        for(var a:db.all(ReservationTable.class)) {
            var other=a.getReservation();
            if(!other.equals(r)&&a.getTable().equals(table)&&active(other)&&start.isBefore(other.getEndsAt().plusSeconds(buffer))&&end.plusSeconds(buffer).isAfter(other.getStartsAt())) throw ApiException.conflict("Table is already reserved for this time");
        }
    }
    void autoAssign(Reservation r) {
        var tables=db.all(RestaurantTable.class).stream().filter(t->id(t.getArea()).equals(id(r.getArea()))&&t.getCapacity()>=r.getGuestCount()).sorted(Comparator.comparingInt(RestaurantTable::getCapacity)).toList();
        for(var t:tables) {try {assign(r,id(t));return;} catch(ApiException ignored) {}}
        throw ApiException.conflict("No free table fits this reservation");
    }
    boolean active(Reservation r) { return Set.of(Reservation.Status.PENDING,Reservation.Status.CONFIRMED,Reservation.Status.ARRIVED,Reservation.Status.SEATED).contains(r.getStatus()); }
    public void command(String type,Map<String,Object> c,User actor) {
        var r=db.get(Reservation.class,required(c,"id",100));
        switch(type) {
            case "edit" -> {
                require(r,Reservation.Status.PENDING,Reservation.Status.CONFIRMED); var input=object(c,"input");
                fill(r,input,false,false);replaceItems(r,input,false);
                String tableId=text(input,"tableId");
                if(!tableId.isEmpty())assign(r,tableId);
                else if(r.getStatus()==Reservation.Status.CONFIRMED)throw ApiException.conflict("Confirmed reservation must keep a table");
                else db.all(ReservationTable.class).stream().filter(a->a.getReservation().equals(r)).forEach(db::remove);
            }
            case "assign", "reservation.move" -> {
                require(r,Reservation.Status.PENDING,Reservation.Status.CONFIRMED,Reservation.Status.ARRIVED,Reservation.Status.SEATED);
                var previous=assignment(r);String tableId=required(c,"tableId",100);
                if(r.getStatus()==Reservation.Status.SEATED) {
                    var session=db.all(TableSession.class).stream().filter(s->r.equals(s.getReservation())&&s.getStatus()!=TableSession.Status.CLOSED).findFirst().orElseThrow(()->ApiException.conflict("No active session"));
                    var table=db.get(RestaurantTable.class,tableId);
                    if(table.getStatus()!=TableStatus.AVAILABLE||db.all(TableSession.class).stream().anyMatch(s->s.getTable().equals(table)&&s.getStatus()!=TableSession.Status.CLOSED))throw ApiException.conflict("Table is not available");
                    assign(r,tableId);session.getTable().setStatus(TableStatus.AVAILABLE);session.setTable(table);table.setStatus(TableStatus.OCCUPIED);
                    service.notify(Role.WAITER,"TABLE_REASSIGNED","Đổi bàn "+previous.getTable().getCode()+" → "+table.getCode(),r.getCode()+" · "+r.getCustomerName(),id(session));
                } else assign(r,tableId);
            }
            case "confirm" -> {require(r,Reservation.Status.PENDING);if(assignment(r)==null)autoAssign(r);r.setStatus(Reservation.Status.CONFIRMED);mailer.send(r,"Đặt bàn đã được xác nhận","Lịch đặt bàn của bạn đã được xác nhận. Hẹn gặp bạn!");}
            case "reject" -> {require(r,Reservation.Status.PENDING);required(c,"reason",1000);r.setStatus(Reservation.Status.REJECTED);mailer.send(r,"Đặt bàn không thành công","Rất tiếc, nhà hàng không thể nhận lịch đặt bàn này. Lý do: "+text(c,"reason"));}
            case "cancel" -> {require(r,Reservation.Status.PENDING,Reservation.Status.CONFIRMED);required(c,"reason",1000);r.setStatus(Reservation.Status.CANCELLED);if(actor==null)service.notify(Role.RECEPTIONIST,"RESERVATION_CANCELLED","Khách hủy đặt bàn",r.getCode()+" · "+text(c,"reason"),id(r));mailer.send(r,"Đặt bàn đã bị hủy","Lịch đặt bàn của bạn đã được hủy. Lý do: "+text(c,"reason"));}
            case "no-show" -> {require(r,Reservation.Status.CONFIRMED);if(Instant.now().isBefore(r.getStartsAt().plusSeconds(db.lock().getNoShowMinutes()*60L)))throw ApiException.conflict("No-show grace period has not elapsed");r.setStatus(Reservation.Status.NO_SHOW);}
            case "check-in" -> {require(r,Reservation.Status.CONFIRMED);if(Instant.now().isBefore(r.getStartsAt().minusSeconds(EARLY_CHECK_IN_MINUTES*60L)))throw ApiException.conflict("Check-in opens "+EARLY_CHECK_IN_MINUTES+" minutes before the booking time");r.setActualGuests(integer(c,"actualGuests",1,200));r.setArrivedAt(Instant.now());r.setStatus(Reservation.Status.ARRIVED);}
            case "handover" -> {
                require(r,Reservation.Status.ARRIVED); var a=assignment(r);
                if(a==null||a.getTable().getCapacity()<r.getActualGuests())throw ApiException.conflict("Assign a table that fits the actual guests first");
                if(a.getTable().getStatus()!=TableStatus.AVAILABLE)throw ApiException.conflict("Table is not available");
                if(db.all(TableSession.class).stream().anyMatch(s->s.getTable().equals(a.getTable())&&s.getStatus()!=TableSession.Status.CLOSED))throw ApiException.conflict("Table already has an active session");
                var now=Instant.now();ensureFree(r,a.getTable(),now,now.plus(Duration.between(r.getStartsAt(),r.getEndsAt())));
                var session=new TableSession();session.setTable(a.getTable());session.setReservation(r);session.setGuestCount(r.getActualGuests()==null?r.getGuestCount():r.getActualGuests());session.setStartedAt(Instant.now());db.save(session);
                var bill=new Bill();bill.setSession(session);bill.setCode("B-"+id(session));db.save(bill);a.getTable().setStatus(TableStatus.OCCUPIED);r.setHandedOverAt(Instant.now());r.setStatus(Reservation.Status.SEATED);
                service.notify(Role.WAITER,"HANDOVER","Bàn "+a.getTable().getCode()+" chờ tiếp nhận",r.getCode()+" · "+r.getActualGuests()+" khách",id(session));
            }
            default -> throw invalid("Unsupported reservation command");
        }
        String label=HISTORY_LABELS.getOrDefault(type,type);
        history(r,actor,type,text(c,"reason").isEmpty()?label:label+": "+text(c,"reason"));
    }
    void require(Reservation r,Reservation.Status... statuses) {if(!Arrays.asList(statuses).contains(r.getStatus()))throw ApiException.conflict("Invalid reservation transition from " + r.getStatus());}
    public ReservationTable assignment(Reservation r) {return db.all(ReservationTable.class).stream().filter(a->a.getReservation().equals(r)).findFirst().orElse(null);}
    void history(Reservation r,User actor,String action,String description) {var h=new ReservationHistory();h.setReservation(r);h.setActor(actor);h.setAction(action);h.setNewStatus(r.getStatus());h.setDescription(description);db.save(h);}
    public Map<String,Object> view(Reservation r) {
        var a=assignment(r);
        return map("id",id(r),"code",r.getCode(),"customer",map("name",r.getCustomerName(),"phone",r.getPhone()),"startsAt",r.getStartsAt(),"guests",r.getGuestCount(),"duration",Duration.between(r.getStartsAt(),r.getEndsAt()).toMinutes(),"areaId",id(r.getArea()),"tableId",a==null?null:id(a.getTable()),"occasion",r.getOccasion(),"email",r.getEmail(),"notes",r.getNote(),"specialRequests",Objects.toString(r.getSpecialRequests(),""),"status",r.getStatus(),"source",r.getSource(),"actualGuests",r.getActualGuests(),"arrivedAt",r.getArrivedAt(),"handedOverAt",r.getHandedOverAt(),"preorder",db.all(ReservationItem.class).stream().filter(i->i.getReservation().equals(r)).map(i->map("menuItemId",id(i.getMenuItem()),"quantity",i.getQuantity(),"note",i.getNote())).toList(),"history",db.all(ReservationHistory.class).stream().filter(h->h.getReservation().equals(r)).map(h->map("at",h.getCreatedAt(),"text",h.getDescription())).toList());
    }
    public Map<String,Object> publicView(Reservation r) {
        var local=r.getStartsAt().atZone(ZoneId.of(db.lock().getTimezone()));
        return map("code",r.getCode(),"status",r.getStatus(),"createdAt",r.getCreatedAt(),"startsAt",r.getStartsAt(),"durationMinutes",Duration.between(r.getStartsAt(),r.getEndsAt()).toMinutes(),"areaId",id(r.getArea()),"areaName",r.getArea().getName(),"date",local.toLocalDate(),"time",local.toLocalTime().toString(),"guests",r.getGuestCount(),"name",r.getCustomerName(),"phone",r.getPhone(),"email",r.getEmail(),"note",r.getNote(),"occasion",r.getOccasion(),"items",db.all(ReservationItem.class).stream().filter(i->i.getReservation().equals(r)).map(i->map("dishId",id(i.getMenuItem()),"name",i.getMenuItem().getName(),"quantity",i.getQuantity())).toList());
    }
}
