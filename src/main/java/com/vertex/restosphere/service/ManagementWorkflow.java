package com.vertex.restosphere.service;

import static com.vertex.restosphere.service.WorkspaceStore.*;
import static com.vertex.restosphere.service.ReservationWorkflow.*;
import com.vertex.restosphere.entity.*;
import com.vertex.restosphere.common.enums.*;
import com.vertex.restosphere.exception.ApiException;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ManagementWorkflow {
    private final WorkspaceStore db;
    private final ReservationWorkflow reservations;
    private final BillingWorkflow billing;
    private final ServiceWorkflow workflow;
    public void command(String type,Map<String,Object> c,User user) {
        switch(type) {
            case "reservation.move" -> {required(c,"reason",1000);reservations.command(type,c,user);}
            case "table.update" -> {var t=db.get(RestaurantTable.class,required(c,"id",100));if(t.getStatus()==TableStatus.OCCUPIED||t.getStatus()==TableStatus.CLEANING||db.all(ReservationTable.class).stream().anyMatch(a->a.getTable().equals(t)&&reservations.active(a.getReservation())))throw ApiException.conflict("Table has active service or reservations");t.setCapacity(integer(c,"seats",1,200));t.setArea(db.get(Area.class,required(c,"areaId",100)));if(!(c.get("inactive") instanceof Boolean))throw invalid("inactive must be boolean");t.setStatus(Boolean.TRUE.equals(c.get("inactive"))?TableStatus.OUT_OF_SERVICE:TableStatus.AVAILABLE);}
            case "employee.assign" -> {var employee=db.get(User.class,required(c,"id",100));if(employee.getRole()==Role.CUSTOMER)throw invalid("Not an employee");employee.setAssignedArea(db.get(Area.class,required(c,"areaId",100)));}
            case "shift.save" -> {
                var in=object(c,"shift");var s=text(in,"id").isEmpty()?new Shift():db.get(Shift.class,text(in,"id"));s.setName(required(in,"name",150));
                try {var zone=ZoneId.of(db.lock().getTimezone());s.setStartsAt(LocalDateTime.parse(required(in,"date",10)+"T"+required(in,"start",5)).atZone(zone).toInstant());s.setEndsAt(LocalDateTime.parse(required(in,"date",10)+"T"+required(in,"end",5)).atZone(zone).toInstant());}catch(DateTimeException e){throw invalid("Invalid shift time");}
                if(!s.getEndsAt().isAfter(s.getStartsAt()))throw invalid("Shift must end after start");s.setMinimumStaff(integer(in,"minimum",1,200));
                if(!(in.get("employeeIds") instanceof List<?> ids)||ids.size()>200)throw invalid("Invalid employeeIds");
                Set<User> employees=new HashSet<>();for(var value:ids){var e=db.get(User.class,value.toString());if(!e.isEnabled()||e.getRole()==Role.CUSTOMER||!employees.add(e))throw invalid("Invalid or duplicate employee");for(var a:db.all(ShiftAssignment.class))if(a.getUser().equals(e)&&!a.getShift().equals(s)&&s.getStartsAt().isBefore(a.getShift().getEndsAt())&&s.getEndsAt().isAfter(a.getShift().getStartsAt()))throw ApiException.conflict("Employee already assigned to an overlapping shift");}
                if(employees.size()<s.getMinimumStaff())throw invalid("Shift minimum staffing not met");if(s.getId()==null)db.save(s);db.all(ShiftAssignment.class).stream().filter(a->a.getShift().equals(s)).forEach(db::remove);db.flush();for(var e:employees){var a=new ShiftAssignment();a.setShift(s);a.setUser(e);db.save(a);}
            }
            case "category.save" -> {var in=object(c,"category");var category=Boolean.TRUE.equals(c.get("creating"))?new MenuCategory():db.get(MenuCategory.class,required(in,"id",100));category.setName(required(in,"name",150));category.setSortOrder(integer(in,"sort",0,10000));if(!(in.get("visible") instanceof Boolean))throw invalid("visible must be boolean");category.setVisible(Boolean.TRUE.equals(in.get("visible")));if(category.getId()==null)db.save(category);}
            case "category.delete" -> {var category=db.get(MenuCategory.class,required(c,"id",100));if(db.all(MenuItem.class).stream().anyMatch(i->i.getCategory().equals(category)))throw ApiException.conflict("Category contains menu items");db.remove(category);}
            case "menu.save" -> {var in=object(c,"item");var item=Boolean.TRUE.equals(c.get("creating"))?new MenuItem():db.get(MenuItem.class,required(in,"id",100));item.setName(required(in,"name",150));item.setCategory(db.get(MenuCategory.class,required(in,"categoryId",100)));item.setPrice(BillingWorkflow.money(in,"price"));item.setDescription(text(in,"description"));item.setImageUrl(text(in,"image"));try{item.setStatus(MenuItemStatus.valueOf(required(in,"status",30)));}catch(IllegalArgumentException e){throw invalid("Invalid menu status");}if(item.getId()==null)db.save(item);}
            case "menu.delete" -> db.get(MenuItem.class,required(c,"id",100)).setStatus(MenuItemStatus.DISCONTINUED);
            case "approval.decide" -> billing.decide(c,user);
            case "incident.update" -> {var i=db.get(Incident.class,required(c,"id",100));if(!text(c,"assigneeId").isEmpty()){var assignee=db.get(User.class,text(c,"assigneeId"));if(!assignee.isEnabled()||assignee.getRole()==Role.CUSTOMER)throw invalid("Invalid assignee");i.setAssignee(assignee);}try{i.setStatus(Incident.Status.valueOf(required(c,"status",30)));}catch(IllegalArgumentException e){throw invalid("Invalid incident status");}String reply=text(c,"reply");if(reply.isEmpty())reply=text(c,"note");if(reply.length()>2000)throw invalid("Reply too long");if(i.getStatus()==Incident.Status.RESOLVED&&reply.isEmpty())throw invalid("Resolution is required");if(!reply.isEmpty())i.setResolution(reply);if(i.getStatus()==Incident.Status.RESOLVED)i.setResolvedAt(Instant.now());workflow.notify(Role.KITCHEN,"INCIDENT_REPLY","Incident updated",reply.isEmpty()?i.getStatus().name():reply,id(i));}
            default -> throw invalid("Unsupported manager command");
        }
    }
    public void admin(String type,Map<String,Object> c,User actor) {
        switch(type) {
            case "user.role", "user.status" -> {
                var u=db.get(User.class,required(c,"id",100));if(u.equals(actor))throw ApiException.conflict("Cannot change your own access");
                if(u.getRole()==Role.ADMIN&&u.isEnabled()&&db.all(User.class).stream().filter(x->x.getRole()==Role.ADMIN&&x.isEnabled()).count()<=1)throw ApiException.conflict("Cannot remove last active administrator");
                if(type.equals("user.role")){try{u.setRole(Role.valueOf(required(c,"role",30)));}catch(IllegalArgumentException e){throw invalid("Invalid role");}}
                else {String status=required(c,"status",20);if(!Set.of("ACTIVE","LOCKED").contains(status))throw invalid("Invalid account status");u.setEnabled(status.equals("ACTIVE"));}
                workflow.audit(actor,type.equals("user.role")?"ROLE_CHANGED":u.isEnabled()?"ACCOUNT_UNLOCKED":"ACCOUNT_LOCKED",u.getId(),"USER",u.getEmail());
            }
            case "restaurant.update" -> {var p=object(c,"restaurant");var s=db.lock();s.setName(required(p,"name",150));s.setAddress(text(p,"address"));s.setPhone(text(p,"hotline"));s.setEmail(text(p,"email"));s.setDescription(text(p,"description"));s.setOpeningHours(text(p,"openingHours"));s.setFacebook(text(p,"facebook"));s.setInstagram(text(p,"instagram"));s.setTiktok(text(p,"tiktok"));s.setLogoUrl(text(p,"logoUrl"));s.setCoverUrl(text(p,"coverUrl"));workflow.audit(actor,"RESTAURANT_UPDATED",s.getId(),"SETTINGS","Restaurant profile updated");}
            case "settings.update" -> {var in=object(c,"settings");var s=db.lock();if(!(in.get("onlineReservations") instanceof Boolean)||!(in.get("emailNotifications") instanceof Boolean))throw invalid("Invalid settings flags");s.setOnlineReservations(Boolean.TRUE.equals(in.get("onlineReservations")));s.setEmailNotifications(Boolean.TRUE.equals(in.get("emailNotifications")));s.setBufferMinutes(integer(in,"bufferMinutes",0,240));s.setHoldMinutes(integer(in,"tableHoldMinutes",0,240));s.setNoShowMinutes(integer(in,"noShowMinutes",0,240));try{ZoneId.of(required(in,"timezone",100));}catch(DateTimeException e){throw invalid("Invalid timezone");}s.setTimezone(text(in,"timezone"));if(!"VND".equals(text(in,"currency")))throw invalid("Only VND is supported");var b=object(in,"billing");var vat=number(b,"vatPercent");var service=number(b,"serviceChargePercent");if(vat.signum()<0||service.signum()<0||vat.intValue()>100||service.intValue()>100||vat.scale()>2||service.scale()>2)throw invalid("Invalid billing rates");s.setVatRate(vat);s.setServiceChargeRate(service);s.setBankAccountNumber(text(b,"bankAccount"));s.setBankName(text(b,"bankName"));s.setBankAccountName(text(b,"bankAccountName"));workflow.audit(actor,"RESTAURANT_UPDATED",s.getId(),"SETTINGS","Settings updated");}
            default -> throw invalid("Unsupported admin command");
        }
    }
}
