package com.vertex.restosphere.service;

import static com.vertex.restosphere.service.WorkspaceStore.*;
import static com.vertex.restosphere.service.ReservationWorkflow.*;
import com.vertex.restosphere.entity.*;
import com.vertex.restosphere.common.enums.*;
import com.vertex.restosphere.exception.ApiException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ServiceWorkflow {
    private final WorkspaceStore db;
    private final BillingWorkflow billing;
    public void waiter(String type,Map<String,Object> c,User user) {
        var session=db.get(TableSession.class,required(c,"sessionId",100));
        if(type.equals("accept")) {
            if(session.getStatus()!=TableSession.Status.OPEN||session.getWaiter()!=null)throw ApiException.conflict("Session cannot be accepted");
            session.setWaiter(user);session.setAcceptedAt(Instant.now());
            for(var p:db.all(ReservationItem.class).stream().filter(p->p.getReservation().equals(session.getReservation())).toList()) {var d=new OrderDraft();d.setSession(session);d.setWaiter(user);d.setMenuItem(p.getMenuItem());d.setQuantity(p.getQuantity());d.setNote(p.getNote());db.save(d);}return;
        }
        if(!user.equals(session.getWaiter()))throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN,"Session belongs to another waiter");
        if(session.getStatus()==TableSession.Status.CLOSED)throw ApiException.conflict("Session is closed");
        var bill=billing.bill(session);
        switch(type) {
            case "add-draft", "edit-draft", "remove-draft", "submit" -> {
                if(session.getStatus()!=TableSession.Status.OPEN||session.getGuestLeftAt()!=null)throw ApiException.conflict("Ordering is closed");billing.editable(bill);
                if(type.equals("add-draft")) {var menu=db.get(MenuItem.class,required(c,"menuItemId",100));available(menu);var d=new OrderDraft();d.setSession(session);d.setWaiter(user);d.setMenuItem(menu);db.save(d);}
                else if(!type.equals("submit")) {var d=db.get(OrderDraft.class,required(c,"draftId",100));if(!d.getSession().equals(session)||!d.getWaiter().equals(user))throw invalid("Draft does not belong to this session");if(type.equals("remove-draft"))db.remove(d);else {var q=number(c,"quantity");validateQuantity(d.getMenuItem(),q);String note=text(c,"note");if(note.length()>1000)throw invalid("Note too long");d.setQuantity(q);d.setNote(note);}}
                else {
                    var drafts=db.all(OrderDraft.class).stream().filter(d->d.getSession().equals(session)&&d.getWaiter().equals(user)).toList();if(drafts.isEmpty())throw invalid("No draft items");
                    var order=new Order();order.setSession(session);order.setWaiter(user);order.setRoundNumber(db.all(Order.class).stream().filter(o->o.getSession().equals(session)).mapToInt(Order::getRoundNumber).max().orElse(0)+1);order.setStatus(Order.Status.SUBMITTED);db.save(order);
                    for(var d:drafts) {available(d.getMenuItem());var i=new OrderItem();i.setOrder(order);i.setMenuItem(d.getMenuItem());i.setItemName(d.getMenuItem().getName());i.setUnit(d.getMenuItem().getPriceUnit());i.setUnitPrice(d.getMenuItem().getPrice());i.setQuantity(d.getQuantity());i.setNote(d.getNote());db.save(i);db.remove(d);}db.flush();billing.recalculate(bill);
                    notify(Role.KITCHEN,"NEW_ORDER","New order",session.getTable().getCode(),id(order));
                }
            }
            case "serve" -> {var i=item(c,session);if(i.getStatus()!=OrderItem.Status.READY)throw ApiException.conflict("Item is not ready");i.setStatus(OrderItem.Status.SERVED);i.setServedAt(Instant.now());}
            case "request-cancel" -> {var i=item(c,session);billing.editable(bill);if(Set.of(OrderItem.Status.CANCELLED,OrderItem.Status.VOIDED,OrderItem.Status.REJECTED).contains(i.getStatus()))throw ApiException.conflict("Item is already removed");billing.request(ApprovalRequest.Type.CANCEL_ITEM,"ORDER_ITEM",i.getId(),BillingWorkflow.whole(i.getUnitPrice().multiply(i.getQuantity())),required(c,"reason",1000),user);}
            case "request-priority" -> {var o=db.get(Order.class,required(c,"orderId",100));if(!o.getSession().equals(session))throw invalid("Order belongs to another session");o.setPriorityAt(Instant.now());o.setPriorityBy(id(user));o.setPriorityReason(required(c,"reason",1000));notify(Role.KITCHEN,"PRIORITY","Priority requested",o.getPriorityReason(),id(o));}
            case "request-payment" -> {
                if(session.getStatus()!=TableSession.Status.OPEN)throw ApiException.conflict("Payment already requested");
                if(db.all(OrderDraft.class).stream().anyMatch(d->d.getSession().equals(session))||billing.items(bill).stream().anyMatch(i->Set.of(OrderItem.Status.SUBMITTED,OrderItem.Status.PREPARING,OrderItem.Status.READY).contains(i.getStatus())))throw ApiException.conflict("Submit drafts and serve or resolve all items first");
                billing.recalculate(bill);if(bill.getTotal().signum()<=0)throw ApiException.conflict("Bill has no payable items");session.setStatus(TableSession.Status.PAYMENT_REQUESTED);bill.setRequestedAt(Instant.now());notify(Role.CASHIER,"PAYMENT_REQUEST","Payment requested",session.getTable().getCode(),id(bill));
            }
            case "withdraw-payment-request" -> {billing.editable(bill);if(session.getStatus()!=TableSession.Status.PAYMENT_REQUESTED)throw ApiException.conflict("Payment not requested");session.setStatus(TableSession.Status.OPEN);bill.setRequestedAt(null);notify(Role.CASHIER,"PAYMENT_REQUEST_WITHDRAWN","Payment request withdrawn",session.getTable().getCode(),id(bill));}
            case "guest-left" -> {if(!Boolean.TRUE.equals(c.get("confirmed"))||bill.getStatus()!=Bill.Status.PAID||session.getGuestLeftAt()!=null)throw ApiException.conflict("Confirm departure after payment");session.setGuestLeftAt(Instant.now());session.getTable().setStatus(TableStatus.CLEANING);}
            case "clean" -> {if(!Boolean.TRUE.equals(c.get("confirmed"))||session.getGuestLeftAt()==null||session.getTable().getStatus()!=TableStatus.CLEANING)throw ApiException.conflict("Guests must leave before cleaning");session.setEndedAt(Instant.now());session.setStatus(TableSession.Status.CLOSED);session.getTable().setStatus(TableStatus.AVAILABLE);if(session.getReservation()!=null)session.getReservation().setStatus(Reservation.Status.COMPLETED);}
            default -> throw invalid("Unsupported waiter command");
        }
    }
    OrderItem item(Map<String,Object> c,TableSession session) {var i=db.get(OrderItem.class,required(c,"itemId",100));if(!i.getOrder().getSession().equals(session))throw invalid("Item belongs to another session");return i;}
    void available(MenuItem item) {if(item.getStatus()!=MenuItemStatus.AVAILABLE||!item.getCategory().isVisible())throw ApiException.conflict("Menu item unavailable");}
    public void kitchen(String type,Map<String,Object> c,User user) {
        switch(type) {
            case "start", "complete", "reject" -> {
                var refs=type.equals("reject")?List.of(map("id",required(c,"itemId",100),"version",c.get("version"))):lines(c,"items");if(refs.isEmpty())throw invalid("Select at least one item");Set<String> seen=new HashSet<>();
                for(var ref:refs) {
                    var i=db.get(OrderItem.class,required(ref,"id",100));if(!seen.add(id(i)))throw invalid("Duplicate item");
                    if(number(ref,"version").compareTo(BigDecimal.valueOf(i.getVersion()))!=0)throw ApiException.conflict("Item changed; refresh");
                    var expected=type.equals("complete")?OrderItem.Status.PREPARING:OrderItem.Status.SUBMITTED;if(i.getStatus()!=expected)throw ApiException.conflict("Invalid item transition");
                    if(type.equals("start")) {i.setStatus(OrderItem.Status.PREPARING);i.setChef(user);i.setStartedAt(Instant.now());}
                    else if(type.equals("complete")) {i.setStatus(OrderItem.Status.READY);i.setReadyAt(Instant.now());notify(Role.WAITER,"READY","Dish ready",i.getItemName(),id(i.getOrder().getSession()));}
                    else {i.setStatus(OrderItem.Status.REJECTED);i.setRejectionReason(required(c,"reason",1000));billing.recalculate(billing.bill(i.getOrder().getSession()));notify(Role.WAITER,"ITEM_REJECTED","Dish rejected",i.getRejectionReason(),id(i.getOrder().getSession()));}
                    audit(user,type,i.getId(),"ORDER_ITEM",type);
                }
            }
            case "set-menu-status", "request-menu-status" -> {var menu=db.get(MenuItem.class,required(c,"menuItemId",100));String status=required(c,"status",30);if(!Set.of("AVAILABLE","SOLD_OUT").contains(status)||menu.getStatus()==MenuItemStatus.DISCONTINUED)throw invalid("Invalid menu status");if(type.equals("set-menu-status")) {menu.setStatus(MenuItemStatus.valueOf(status));notify(Role.WAITER,"MENU_AVAILABILITY","Menu availability updated",menu.getName(),id(menu));}else {var r=billing.request(ApprovalRequest.Type.MENU_STATUS,"MENU_ITEM",menu.getId(),BigDecimal.ZERO,"Menu availability: "+status,user);r.setMenuStatus(status);}}
            case "report-incident" -> {var in=object(c,"input");var incident=new Incident();String kind=required(in,"type",50);if(!Set.of("OUT_OF_STOCK","EQUIPMENT","CANNOT_PREPARE","NOTE_CLARIFICATION","OTHER").contains(kind))throw invalid("Invalid incident type");incident.setType(kind);incident.setTitle(kind);incident.setDescription(required(in,"description",2000));try {incident.setSeverity(Incident.Severity.valueOf(required(in,"severity",20)));}catch(IllegalArgumentException e){throw invalid("Invalid severity");}incident.setReporter(user);if(!text(in,"itemId").isEmpty()) {var item=db.get(OrderItem.class,text(in,"itemId"));if(!text(in,"orderId").isEmpty()&&!id(item.getOrder()).equals(text(in,"orderId")))throw invalid("Incident item/order mismatch");incident.setOrderItem(item);}db.save(incident);notify(Role.MANAGER,"INCIDENT","Kitchen incident",incident.getDescription(),id(incident));}
            default -> throw invalid("Unsupported kitchen command");
        }
    }
    public void notify(Role role,String kind,String title,String body,String target) {var n=new Notification();n.setAudienceRole(role);n.setKind(kind);n.setTitle(title);n.setBody(body);n.setPath(target);db.save(n);}
    public void audit(User actor,String action,UUID target,String entity,String description) {var a=new AuditLog();a.setActor(actor);a.setAction(action);a.setTargetId(target);a.setTargetType(entity);a.setDescription(description);db.save(a);}
    public void read(Map<String,Object> c,User user) {
        Set<String> ids=new HashSet<>();if(c.get("ids") instanceof List<?> list)list.forEach(x->ids.add(x.toString()));if(!text(c,"id").isEmpty())ids.add(text(c,"id"));
        for(var n:db.all(Notification.class))if((ids.isEmpty()||ids.contains(id(n)))&&(n.getAudienceRole()==user.getRole()||user.equals(n.getRecipient()))&&db.all(NotificationRead.class).stream().noneMatch(r->r.getNotification().equals(n)&&r.getUser().equals(user))) {var read=new NotificationRead();read.setNotification(n);read.setUser(user);read.setReadAt(Instant.now());db.save(read);}
    }
}
