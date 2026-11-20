package com.vertex.restosphere.service.impl;

import static com.vertex.restosphere.service.impl.WorkspaceStoreImpl.*;
import static com.vertex.restosphere.service.impl.ReservationWorkflowImpl.*;
import com.vertex.restosphere.service.BillingWorkflow;
import com.vertex.restosphere.service.ServiceWorkflow;
import com.vertex.restosphere.service.WorkspaceStore;
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
public class ServiceWorkflowImpl implements ServiceWorkflow {
    private final WorkspaceStore db;
    private final BillingWorkflow billing;
    @Override public void kitchen(String type,Map<String,Object> c,User user) {
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
    @Override public void notify(Role role,String kind,String title,String body,String target) {var n=new Notification();n.setAudienceRole(role);n.setKind(kind);n.setTitle(title);n.setBody(body);n.setPath(target);db.save(n);}
    @Override public void audit(User actor,String action,UUID target,String entity,String description) {var a=new AuditLog();a.setActor(actor);a.setAction(action);a.setTargetId(target);a.setTargetType(entity);a.setDescription(description);db.save(a);}
    @Override public void read(Map<String,Object> c,User user) {
        Set<String> ids=new HashSet<>();if(c.get("ids") instanceof List<?> list)list.forEach(x->ids.add(x.toString()));if(!text(c,"id").isEmpty())ids.add(text(c,"id"));
        for(var n:db.all(Notification.class))if((ids.isEmpty()||ids.contains(id(n)))&&(n.getAudienceRole()==user.getRole()||user.equals(n.getRecipient()))&&db.all(NotificationRead.class).stream().noneMatch(r->r.getNotification().equals(n)&&r.getUser().equals(user))) {var read=new NotificationRead();read.setNotification(n);read.setUser(user);read.setReadAt(Instant.now());db.save(read);}
    }
}
