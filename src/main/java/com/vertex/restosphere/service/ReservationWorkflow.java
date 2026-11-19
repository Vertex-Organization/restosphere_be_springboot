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
    public Map<String,Object> settings() {
        var s=db.lock();
        return map("onlineReservations",s.isOnlineReservations(),"bufferMinutes",s.getBufferMinutes(),"noShowMinutes",s.getNoShowMinutes(),"tableHoldMinutes",s.getHoldMinutes(),"timezone",s.getTimezone(),"areas",db.all(Area.class).stream().map(a->map("id",id(a),"name",a.getName())).toList());
    }
    public Map<String,Object> createPublic(Map<String,Object> input) {
        var s=db.lock(); if(!s.isOnlineReservations()) throw ApiException.conflict("Online reservations are disabled");
        var r=create(input,null,true,false); s.setRevision(s.getRevision()+1); db.flush(); return publicView(r);
    }
    public Reservation create(Map<String,Object> input,User actor,boolean web,boolean walkIn) {
        var r=new Reservation(); r.setCode("RS-"+UUID.randomUUID().toString().replace("-", "").substring(0,16).toUpperCase());
        r.setSource(web?Reservation.Source.WEB:walkIn?Reservation.Source.WALK_IN:Reservation.Source.PHONE);
        fill(r,input,web,walkIn); db.save(r); history(r,actor,"CREATED","Reservation created");
        if(!text(input,"tableId").isEmpty()) assign(r,text(input,"tableId"));
        replaceItems(r,input,web); return r;
    }
    void fill(Reservation r,Map<String,Object> input,boolean web,boolean walkIn) {
        var customer=web?input:object(input,"customer"); r.setCustomerName(required(customer,web?"name":"name",150));
        String phone=required(customer,"phone",30).replaceAll("[\\s()-]", "");
        if(!phone.matches("\\+?[0-9]{8,15}")) throw invalid("Invalid phone"); r.setPhone(phone);
        String email=text(input,"email"); if(!email.isEmpty() && (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")||email.length()>254)) throw invalid("Invalid email"); r.setEmail(email);
        r.setGuestCount(integer(input,"guests",1,200));
        String areaId=required(input,"areaId",100); r.setArea(db.get(Area.class,areaId));
        Instant start;
        try { start=text(input,"startsAt").isEmpty()? LocalDateTime.parse(required(input,"date",10)+"T"+required(input,"time",5)).atZone(ZoneId.of(db.lock().getTimezone())).toInstant():Instant.parse(text(input,"startsAt")); }
        catch(DateTimeException e) { throw invalid("Invalid reservation date/time"); }
        if(start.isBefore(Instant.now().minusSeconds(walkIn?1800:60)) || start.isAfter(Instant.now().plus(Duration.ofDays(365)))) throw invalid("Reservation date is outside booking window");
        int duration=input.containsKey(web?"durationMinutes":"duration")?integer(input,web?"durationMinutes":"duration",15,720):120;
        r.setStartsAt(start); r.setEndsAt(start.plusSeconds(duration*60L));
        String note=text(input,web?"note":"notes"); if(note.length()>1000) throw invalid("Note too long"); r.setNote(note);
        r.setOccasion(text(input,"occasion")); r.setSpecialRequests(text(input,"specialRequests"));
        if(db.all(RestaurantTable.class).stream().filter(t->id(t.getArea()).equals(id(r.getArea()))&&t.getStatus()!=TableStatus.OUT_OF_SERVICE).mapToInt(RestaurantTable::getCapacity).sum()<r.getGuestCount()) throw ApiException.conflict("Area has insufficient capacity");
    }
    void replaceItems(Reservation r,Map<String,Object> input,boolean web) {
        db.all(ReservationItem.class).stream().filter(i->i.getReservation().equals(r)).forEach(db::remove);
        for(var line:lines(input,web?"items":"preorder")) {
            var menu=db.get(MenuItem.class,required(line,web?"dishId":"menuItemId",100));
            if(menu.getStatus()!=MenuItemStatus.AVAILABLE||!menu.getCategory().isVisible()) throw ApiException.conflict("Preorder item unavailable");
            var quantity=number(line,"quantity"); validateQuantity(menu,quantity);
            var item=new ReservationItem(); item.setReservation(r); item.setMenuItem(menu); item.setQuantity(quantity); item.setUnitPrice(menu.getPrice()); item.setNote(text(line,"note")); db.save(item);
        }
    }
    public static void validateQuantity(MenuItem item,BigDecimal quantity) {
        if(quantity.signum()<=0||quantity.compareTo(new BigDecimal("999"))>0||quantity.stripTrailingZeros().scale()>3||(!"kg".equals(item.getPriceUnit())&&quantity.stripTrailingZeros().scale()>0)) throw invalid("Invalid quantity for item unit");
    }
    public Map<String,Object> lookup(Map<String,Object> input) {
        db.lock(); String code=required(input,"code",30), phone=required(input,"phone",30).replaceAll("[\\s()-]", "");
        return db.all(Reservation.class).stream().filter(r->r.getCode().equalsIgnoreCase(code)&&r.getPhone().equals(phone)).findFirst().map(this::publicView).orElseThrow(()->ApiException.notFound("Reservation"));
    }
    public void assign(Reservation r,String tableId) {
        var table=db.get(RestaurantTable.class,tableId);
        if(!id(table.getArea()).equals(id(r.getArea()))||table.getCapacity()<r.getGuestCount()||table.getStatus()==TableStatus.OUT_OF_SERVICE) throw ApiException.conflict("Table does not match reservation");
        long buffer=db.lock().getBufferMinutes()*60L;
        for(var a:db.all(ReservationTable.class)) {
            var other=a.getReservation();
            if(!other.equals(r)&&a.getTable().equals(table)&&active(other)&&r.getStartsAt().isBefore(other.getEndsAt().plusSeconds(buffer))&&r.getEndsAt().plusSeconds(buffer).isAfter(other.getStartsAt())) throw ApiException.conflict("Table is already reserved for this time");
        }
        db.all(ReservationTable.class).stream().filter(a->a.getReservation().equals(r)).forEach(db::remove); db.flush();
        var a=new ReservationTable();a.setReservation(r);a.setTable(table);db.save(a);
    }
    boolean active(Reservation r) { return Set.of(Reservation.Status.PENDING,Reservation.Status.CONFIRMED,Reservation.Status.ARRIVED,Reservation.Status.SEATED).contains(r.getStatus()); }
    public void command(String type,Map<String,Object> c,User actor) {
        var r=db.get(Reservation.class,required(c,"id",100));
        switch(type) {
            case "edit" -> { require(r,Reservation.Status.PENDING,Reservation.Status.CONFIRMED); fill(r,object(c,"input"),false,false);replaceItems(r,object(c,"input"),false); var a=assignment(r);if(a!=null)assign(r,id(a.getTable())); }
            case "assign", "reservation.move" -> {require(r,Reservation.Status.PENDING,Reservation.Status.CONFIRMED,Reservation.Status.ARRIVED);assign(r,required(c,"tableId",100));}
            case "confirm" -> {require(r,Reservation.Status.PENDING);if(assignment(r)==null)throw ApiException.conflict("Assign a table before confirmation");r.setStatus(Reservation.Status.CONFIRMED);}
            case "reject" -> {require(r,Reservation.Status.PENDING);required(c,"reason",1000);r.setStatus(Reservation.Status.REJECTED);}
            case "cancel" -> {require(r,Reservation.Status.PENDING,Reservation.Status.CONFIRMED);required(c,"reason",1000);r.setStatus(Reservation.Status.CANCELLED);}
            case "no-show" -> {require(r,Reservation.Status.CONFIRMED);if(Instant.now().isBefore(r.getStartsAt().plusSeconds(db.lock().getNoShowMinutes()*60L)))throw ApiException.conflict("No-show grace period has not elapsed");r.setStatus(Reservation.Status.NO_SHOW);}
            case "check-in" -> {require(r,Reservation.Status.CONFIRMED);var a=assignment(r);int guests=integer(c,"actualGuests",1,200);if(a==null||a.getTable().getCapacity()<guests)throw ApiException.conflict("Assigned table capacity exceeded");r.setActualGuests(guests);r.setArrivedAt(Instant.now());r.setStatus(Reservation.Status.ARRIVED);}
            case "handover" -> {
                require(r,Reservation.Status.ARRIVED); var a=assignment(r);if(a==null||a.getTable().getStatus()!=TableStatus.AVAILABLE)throw ApiException.conflict("Table is not available");
                if(db.all(TableSession.class).stream().anyMatch(s->s.getTable().equals(a.getTable())&&s.getStatus()!=TableSession.Status.CLOSED))throw ApiException.conflict("Table already has an active session");
                var session=new TableSession();session.setTable(a.getTable());session.setReservation(r);session.setGuestCount(r.getActualGuests()==null?r.getGuestCount():r.getActualGuests());session.setStartedAt(Instant.now());db.save(session);
                var bill=new Bill();bill.setSession(session);bill.setCode(code("B",session));db.save(bill);a.getTable().setStatus(TableStatus.OCCUPIED);r.setHandedOverAt(Instant.now());r.setStatus(Reservation.Status.SEATED);
            }
            default -> throw invalid("Unsupported reservation command");
        }
        history(r,actor,type,text(c,"reason").isEmpty()?type:text(c,"reason"));
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
        return map("code",r.getCode(),"status",r.getStatus(),"createdAt",r.getCreatedAt(),"startsAt",r.getStartsAt(),"durationMinutes",Duration.between(r.getStartsAt(),r.getEndsAt()).toMinutes(),"areaId",id(r.getArea()),"area","main","date",local.toLocalDate(),"time",local.toLocalTime().toString(),"guests",r.getGuestCount(),"name",r.getCustomerName(),"phone",r.getPhone(),"email",r.getEmail(),"note",r.getNote(),"occasion",r.getOccasion(),"items",db.all(ReservationItem.class).stream().filter(i->i.getReservation().equals(r)).map(i->map("dishId",id(i.getMenuItem()),"quantity",i.getQuantity())).toList());
    }
}
