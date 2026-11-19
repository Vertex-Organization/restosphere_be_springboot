package com.vertex.restosphere.service;

import static com.vertex.restosphere.service.WorkspaceStore.*;
import com.vertex.restosphere.entity.*;
import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.exception.ApiException;
import java.time.*;
import java.util.*;
import java.util.function.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WorkspaceSnapshots {
    private final WorkspaceStore db;
    private final ReservationWorkflow reservations;
    private final BillingWorkflow billing;
    static String str(Object o) {return Objects.toString(o,"");}
    static final Map<String,Set<String>> FIELDS=Map.of(
            "kitchen",Set.of("schema","seedDay","revision","nextId","nextTicket","staff","orders","items","menuCategories","menuItems","menuRequests","incidents","notifications","outbox"),
            "cashier",Set.of("schema","seedDay","revision","nextId","counters","config","staff","menu","tables","sessions","orders","items","bills","payments","requests","shifts","scheduledShifts","notifications","outbox","processedKeys"),
            "manager",Set.of("schema","revision","seedDay","areas","tables","sessions","reservations","orders","employees","shifts","categories","menu","approvals","incidents","notifications","bills","payments","history","outbox"),
            "staff",Set.of("version","serviceVersion","revision","settings","seedDay","areas","tables","reservations","notifications","assignments","nextId","employees","tableSessions","menuCategories","menuItems","orders","orderItems","drafts","bills","waiterNotifications","requests","outbox"));

    static <T,K> Map<K,List<T>> group(List<T> list,Function<T,K> key) {
        Map<K,List<T>> m=new HashMap<>();
        for(var x:list){var k=key.apply(x);if(k!=null)m.computeIfAbsent(k,z->new ArrayList<>()).add(x);}
        return m;
    }
    static <T,K> Map<K,T> first(List<T> list,Function<T,K> key) {Map<K,T> m=new HashMap<>();for(var x:list)m.putIfAbsent(key.apply(x),x);return m;}

    /** Loads each table at most once per snapshot and indexes it, instead of re-reading whole tables for every row. */
    final class Ctx {
        final RestaurantSettings s;final User user;final String workspace;final ZoneId zone;final Instant now=Instant.now();
        private final Map<Object,Object> memo=new HashMap<>();
        Ctx(RestaurantSettings s,User user,String workspace){this.s=s;this.user=user;this.workspace=workspace;this.zone=ZoneId.of(s.getTimezone());}
        @SuppressWarnings("unchecked") <T> T memo(Object key,Supplier<T> f) {var v=(T)memo.get(key);if(v==null){v=f.get();memo.put(key,v);}return v;}
        <T> List<T> all(Class<T> type) {return memo(type,()->db.all(type));}
        List<AuditLog> audit(UUID target) {return memo("audit",()->group(all(AuditLog.class),AuditLog::getTargetId)).getOrDefault(target,List.of());}
        List<OrderItem> itemsOf(Order o) {return memo("itemsByOrder",()->group(all(OrderItem.class),i->i.getOrder().getId())).getOrDefault(o.getId(),List.of());}
        List<OrderItem> itemsOf(Bill b) {return memo("itemsBySession",()->group(all(OrderItem.class),i->i.getOrder().getSession().getId())).getOrDefault(b.getSession().getId(),List.of());}
        Bill bill(TableSession session) {
            var b=memo("billBySession",()->first(all(Bill.class),x->x.getSession().getId())).get(session.getId());
            if(b==null)throw ApiException.notFound("Bill");
            return b;
        }
        TableSession openSession(RestaurantTable t) {return memo("openSession",()->first(all(TableSession.class).stream().filter(x->x.getStatus()!=TableSession.Status.CLOSED).toList(),x->x.getTable().getId())).get(t.getId());}
        List<ShiftAssignment> assignmentsOf(User u) {return memo("assignByUser",()->group(all(ShiftAssignment.class),a->a.getUser().getId())).getOrDefault(u.getId(),List.of());}
        List<ShiftAssignment> assignmentsOf(Shift sh) {return memo("assignByShift",()->group(all(ShiftAssignment.class),a->a.getShift().getId())).getOrDefault(sh.getId(),List.of());}
        List<NotificationRead> readsOf(Notification n) {return memo("reads",()->group(all(NotificationRead.class),r->r.getNotification().getId())).getOrDefault(n.getId(),List.of());}
        boolean mine(User owner) {return owner!=null&&owner.getId().equals(user.getId());}
        List<Map<String,Object>> staff() {return memo("staffList",()->all(User.class).stream().filter(u->u.getRole()!=Role.CUSTOMER).map(u->WorkspaceSnapshots.this.staff(u,this)).toList());}
        List<Map<String,Object>> sessions() {return memo("sessions",()->all(TableSession.class).stream().map(t->map("id",id(t),"tableId",id(t.getTable()),"reservationId",id(t.getReservation()),"waiterId",id(t.getWaiter()),"guests",t.getGuestCount(),"startedAt",t.getStartedAt(),"openedAt",t.getStartedAt(),"handedOverAt",t.getStartedAt(),"acceptedAt",t.getAcceptedAt(),"guestLeftAt",t.getGuestLeftAt(),"closedAt",t.getEndedAt(),"paymentRequested",t.getStatus()==TableSession.Status.PAYMENT_REQUESTED)).toList());}
        List<Map<String,Object>> categories() {return memo("categories",()->all(MenuCategory.class).stream().map(c->map("id",id(c),"name",c.getName(),"visible",c.isVisible(),"sort",c.getSortOrder(),"sortOrder",c.getSortOrder(),"menuGroup",c.getMenuGroup())).toList());}
        List<Map<String,Object>> menu() {return memo("menu",()->all(MenuItem.class).stream().map(i->map("id",id(i),"name",i.getName(),"categoryId",id(i.getCategory()),"price",i.getPrice(),"priceUnit",i.getPriceUnit(),"unit",i.getPriceUnit(),"image",str(i.getImageUrl()),"imageUrl",i.getImageUrl(),"description",str(i.getDescription()),"status",i.getStatus(),"updatedAt",i.getUpdatedAt())).toList());}
        List<Order> submittedOrders() {return memo("submittedOrders",()->all(Order.class).stream().filter(o->o.getStatus()!=Order.Status.DRAFT).toList());}
        List<Map<String,Object>> items() {return memo("items",()->all(OrderItem.class).stream().map(i->item(i,this)).toList());}
        List<Map<String,Object>> notifications() {return memo("notifications",()->all(Notification.class).stream().filter(n->n.getAudienceRole()==user.getRole()||mine(n.getRecipient())).map(n->notification(n,this)).toList());}
        Map<String,Object> approval(ApprovalRequest r) {return memo(r.getId(),()->WorkspaceSnapshots.this.approval(r,this));}
        List<Map<String,Object>> approvals(Predicate<ApprovalRequest> keep) {return all(ApprovalRequest.class).stream().filter(keep).map(this::approval).toList();}
    }

    public Map<String,Object> snapshot(String workspace,User user) {
        var s=db.lock();db.flush();
        if(workspace.equals("admin"))return admin(s);
        var c=new Ctx(s,user,workspace);
        var fields=FIELDS.getOrDefault(workspace,FIELDS.get("staff"));
        Map<String,Object> data=new LinkedHashMap<>();
        BiConsumer<String,Supplier<Object>> put=(key,value)->{if(fields.contains(key))data.put(key,value.get());};
        boolean manager=workspace.equals("manager"),cashier=workspace.equals("cashier");
        put.accept("schema",()->1);put.accept("version",()->1);put.accept("serviceVersion",()->1);
        put.accept("revision",s::getRevision);put.accept("seedDay",()->LocalDate.now(c.zone));put.accept("nextId",()->s.getRevision()+1);
        put.accept("nextTicket",()->c.all(Order.class).size()+1);
        put.accept("staff",c::staff);put.accept("employees",c::staff);
        put.accept("areas",()->c.all(Area.class).stream().map(a->map("id",id(a),"name",a.getName())).toList());
        put.accept("tables",()->c.all(RestaurantTable.class).stream().map(t->{var open=c.openSession(t);return map("id",id(t),"code",t.getCode(),"areaId",id(t.getArea()),"area",t.getArea().getName(),"capacity",t.getCapacity(),"seats",t.getCapacity(),"status",t.getStatus(),"sessionId",open==null?null:id(open));}).toList());
        put.accept("sessions",c::sessions);put.accept("tableSessions",c::sessions);
        put.accept("categories",c::categories);put.accept("menuCategories",c::categories);
        put.accept("menu",c::menu);put.accept("menuItems",c::menu);
        put.accept("orders",()->c.submittedOrders().stream().map(o->order(o,c)).toList());
        put.accept("items",c::items);put.accept("orderItems",c::items);
        put.accept("bills",()->manager
                ?c.all(Bill.class).stream().filter(b->b.getStatus()==Bill.Status.PAID).map(b->map("id",id(b),"at",b.getPaidAt(),"shift",b.getPaidAt().atZone(c.zone).getHour()<16?"LUNCH":"DINNER","guests",b.getSession().getGuestCount(),"discount",b.getDiscount(),"items",c.itemsOf(b).stream().filter(i->!Set.of(OrderItem.Status.CANCELLED,OrderItem.Status.VOIDED,OrderItem.Status.REJECTED).contains(i.getStatus())).map(i->map("name",i.getItemName(),"categoryId",id(i.getMenuItem().getCategory()),"quantity",i.getQuantity(),"unitPrice",i.getUnitPrice())).toList())).toList()
                :c.all(Bill.class).stream().map(b->billing.view(b,c.audit(b.getId()))).toList());
        put.accept("payments",()->manager
                ?c.all(Payment.class).stream().map(p->map("id",id(p),"billId",id(p.getBill()),"at",p.getPaidAt()==null?p.getCreatedAt():p.getPaidAt(),"method",p.getMethod(),"amount",p.getAmount(),"status",p.getStatus()==Payment.Status.COMPLETED?"COMPLETED":p.getStatus()==Payment.Status.PENDING?"PENDING":"FAILED")).toList()
                :c.all(Payment.class).stream().map(billing::payment).toList());
        put.accept("approvals",()->c.approvals(r->true));
        put.accept("requests",()->c.approvals(
                workspace.equals("staff")?r->r.getType()==ApprovalRequest.Type.CANCEL_ITEM||r.getType()==ApprovalRequest.Type.RESERVATION
                :cashier?r->Set.of(ApprovalRequest.Type.DISCOUNT,ApprovalRequest.Type.BILL_ADJUSTMENT,ApprovalRequest.Type.PAYMENT_VOID).contains(r.getType())
                :r->true));
        put.accept("notifications",c::notifications);put.accept("waiterNotifications",c::notifications);
        put.accept("settings",reservations::settings);
        put.accept("drafts",()->c.all(OrderDraft.class).stream().filter(d->c.mine(d.getWaiter())).map(d->map("id",id(d),"sessionId",id(d.getSession()),"waiterId",id(d.getWaiter()),"menuItemId",id(d.getMenuItem()),"quantity",d.getQuantity(),"note",str(d.getNote()))).toList());
        put.accept("assignments",()->c.all(ReservationTable.class).stream().map(a->map("id",id(a),"reservationId",id(a.getReservation()),"tableId",id(a.getTable()),"assignedAt",a.getCreatedAt(),"releasedAt",reservations.active(a.getReservation())?null:a.getReservation().getUpdatedAt())).toList());
        put.accept("reservations",()->{
            var assignment=first(c.all(ReservationTable.class),a->a.getReservation().getId());
            var preorder=group(c.all(ReservationItem.class),i->i.getReservation().getId());
            var history=group(c.all(ReservationHistory.class),h->h.getReservation().getId());
            return c.all(Reservation.class).stream().map(r->{
                var v=reservations.view(r,assignment.get(r.getId()),preorder.getOrDefault(r.getId(),List.of()),history.getOrDefault(r.getId(),List.of()).stream().sorted(ReservationWorkflow.HISTORY_ORDER).toList());
                if(manager)v.putAll(map("customer",r.getCustomerName(),"phone",r.getPhone(),"at",r.getStartsAt(),"until",r.getEndsAt(),"tableId",v.getOrDefault("tableId",""),"employeeId","","note",str(r.getNote())));
                return v;}).toList();});
        put.accept("incidents",()->{
            var managers=c.all(User.class).stream().filter(u->u.getRole()==Role.MANAGER).map(WorkspaceStore::id).toList();
            return c.all(Incident.class).stream().map(i->map("id",id(i),"code","I-"+id(i),"type",i.getType(),"severity",i.getSeverity(),"status",i.getStatus(),"title",i.getTitle(),"description",i.getDescription(),"orderId",i.getOrderItem()==null?null:id(i.getOrderItem().getOrder()),"itemId",i.getOrderItem()==null?null:id(i.getOrderItem()),"reporterId",id(i.getReporter()),"assigneeId",id(i.getAssignee()),"at",i.getCreatedAt(),"notifiedStaffIds",managers,"response",i.getResolution()==null?null:map("by",id(i.getAssignee()),"at",i.getUpdatedAt(),"text",i.getResolution()))).toList();});
        put.accept("menuRequests",()->c.all(ApprovalRequest.class).stream().filter(r->r.getType()==ApprovalRequest.Type.MENU_STATUS).map(r->map("id",id(r),"menuItemId",r.getTargetId(),"requested",r.getMenuStatus(),"requestedBy",id(r.getRequestedBy()),"requestedAt",r.getCreatedAt(),"status",r.getStatus(),"decidedAt",r.getDecidedAt(),"decidedBy",id(r.getDecidedBy()))).toList());
        put.accept("history",()->c.all(AuditLog.class).stream().map(a->map("id",id(a),"at",a.getCreatedAt(),"actorId",id(a.getActor()),"entityId",a.getTargetId(),"text",a.getDescription())).toList());
        put.accept("shifts",()->cashier
                ?c.all(CashierShift.class).stream().map(x->map("id",id(x),"code","S-"+id(x),"cashierId",id(x.getCashier()),"status",x.getClosedAt()==null?"OPEN":"CLOSED","openedAt",x.getOpenedAt(),"openingFloat",x.getOpeningCash(),"closedAt",x.getClosedAt(),"expectedCash",x.getExpectedCash(),"countedCash",x.getCountedCash(),"closingNote",x.getNote())).toList()
                :c.all(Shift.class).stream().map(x->shift(x,c)).toList());
        put.accept("scheduledShifts",()->c.all(ShiftAssignment.class).stream().filter(a->a.getUser().getRole()==Role.CASHIER).map(a->map("id",id(a.getShift()),"name",a.getShift().getName(),"start",a.getShift().getStartsAt(),"end",a.getShift().getEndsAt(),"cashierId",id(a.getUser()))).toList());
        put.accept("config",()->{
            var config=map("restaurantName",s.getName(),"address",s.getAddress(),"serviceChargeRate",s.getServiceChargeRate().movePointRight(2),"taxRate",s.getVatRate().movePointRight(2),"methods",map("CASH",true,"TRANSFER",!str(s.getBankAccountNumber()).isEmpty(),"CARD",false),"bank",str(s.getBankAccountNumber()).isEmpty()?null:map("bankName",str(s.getBankName()),"accountNumber",s.getBankAccountNumber(),"accountName",str(s.getBankAccountName())));
            config.putIfAbsent("bank",null);return config;});
        put.accept("counters",()->map("bill",c.all(Bill.class).size(),"payment",c.all(Payment.class).size(),"request",c.all(ApprovalRequest.class).size(),"shift",c.all(CashierShift.class).size(),"order",c.submittedOrders().size()));
        put.accept("processedKeys",()->{Map<String,Object> keys=new LinkedHashMap<>();c.all(Payment.class).stream().filter(p->id(user).equals(p.getCreatedBy())&&p.getIdempotencyKey()!=null).forEach(p->keys.put(p.getIdempotencyKey(),id(p)));return keys;});
        put.accept("outbox",c::notifications);
        return data;
    }
    Map<String,Object> staff(User u,Ctx c) {
        List<String> permissions=switch(c.workspace){case "cashier"->List.of("bills.view","bills.prepare","payments.create","payments.view","payments.confirm_transfer","receipts.print","transactions.view","discounts.apply","discounts.request","requests.create","shifts.view","shifts.manage");case "kitchen"->List.of("kitchen.items.update","kitchen.incidents.create","kitchen.menu.availability");default->List.of("tables.edit","reservations.edit","employees.assign","shifts.manage","menu.manage","approvals.decide","incidents.manage","reports.export");};
        return map("id",id(u),"name",u.getFullName(),"fullName",u.getFullName(),"username",u.getEmail(),"role",u.getRole(),"position",u.getRole().name(),"shift",c.assignmentsOf(u).stream().filter(a->a.getShift().getStartsAt().isBefore(c.now)&&a.getShift().getEndsAt().isAfter(c.now)).map(a->a.getShift().getName()).findFirst().orElse(""),"permissions",permissions,"discountLimitPercent",10,"approvalLimit",999999999999L,"department",switch(u.getRole()){case WAITER->"SERVICE";case KITCHEN->"KITCHEN";case CASHIER->"CASHIER";case RECEPTIONIST->"RECEPTION";default->"MANAGEMENT";},"status",u.isEnabled()?"ACTIVE":"INACTIVE","areaId",id(u.getAssignedArea()));
    }
    Map<String,Object> order(Order o,Ctx c) {var t=o.getSession().getTable();return map("id",id(o),"code","O-"+id(o),"sessionId",id(o.getSession()),"tableCode",t.getCode(),"area",t.getArea().getName(),"guests",o.getSession().getGuestCount(),"waiterId",id(o.getWaiter()),"employeeId",id(o.getWaiter()),"sequence",o.getRoundNumber(),"round",o.getRoundNumber(),"submittedAt",o.getCreatedAt(),"at",o.getCreatedAt(),"paid",c.bill(o.getSession()).getStatus()==Bill.Status.PAID,"priority",o.getPriorityAt()==null?null:map("at",o.getPriorityAt(),"by",o.getPriorityBy(),"reason",o.getPriorityReason()),"items",c.itemsOf(o).stream().map(i->item(i,c)).toList());}
    Map<String,Object> item(OrderItem i,Ctx c) {return map("id",id(i),"orderId",id(i.getOrder()),"menuItemId",id(i.getMenuItem()),"menuId",id(i.getMenuItem()),"name",i.getItemName(),"unitPrice",i.getUnitPrice(),"quantity",i.getQuantity(),"note",str(i.getNote()),"status",i.getStatus(),"version",i.getVersion(),"submittedAt",i.getCreatedAt(),"startedAt",i.getStartedAt(),"startedBy",id(i.getChef()),"chefId",id(i.getChef()),"readyAt",i.getReadyAt(),"readyBy",id(i.getChef()),"servedAt",i.getServedAt(),"rejectionReason",i.getRejectionReason(),"cancellationReason",i.getRejectionReason(),"history",c.audit(i.getId()).stream().map(a->map("at",a.getCreatedAt(),"by",id(a.getActor()),"status",switch(a.getAction()){case "start"->"PREPARING";case "complete"->"READY";case "reject"->"REJECTED";default->i.getStatus().name();},"note",a.getDescription())).toList());}
    Map<String,Object> approval(ApprovalRequest r,Ctx c) {
        Bill b=null;OrderItem item=null;Payment payment=null;
        if("BILL".equals(r.getTargetType()))b=db.get(Bill.class,r.getTargetId().toString());
        if("ORDER_ITEM".equals(r.getTargetType())){item=db.get(OrderItem.class,r.getTargetId().toString());b=c.bill(item.getOrder().getSession());}
        if("PAYMENT".equals(r.getTargetType())){payment=db.get(Payment.class,r.getTargetId().toString());b=payment.getBill();}
        return map("id",id(r),"code","R-"+id(r),"type",r.getType(),"status",r.getStatus(),"targetType",r.getTargetType(),"targetId",r.getTargetId(),"billId",b==null?null:id(b),"paymentId",payment==null?null:id(payment),"itemId",item==null?null:id(item),"sessionId",b==null?null:id(b.getSession()),"amount",r.getAmount(),"menuStatus",r.getMenuStatus(),"discount",r.getDiscountValue()==null?null:map("mode",r.getDiscountMode(),"value",r.getDiscountValue(),"amount",r.getAmount()),"reason",r.getReason(),"requestedBy",id(r.getRequestedBy()),"requestedAt",r.getCreatedAt(),"decidedBy",r.getDecidedBy()==null?null:id(r.getDecidedBy()),"decidedAt",r.getDecidedAt(),"decisionNote",r.getDecisionNote());
    }
    Map<String,Object> shift(Shift s,Ctx c) {return map("id",id(s),"name",s.getName(),"date",s.getStartsAt().atZone(c.zone).toLocalDate(),"start",s.getStartsAt().atZone(c.zone).toLocalTime().toString(),"end",s.getEndsAt().atZone(c.zone).toLocalTime().toString(),"minimum",s.getMinimumStaff(),"employeeIds",c.assignmentsOf(s).stream().map(a->id(a.getUser())).toList());}
    Map<String,Object> notification(Notification n,Ctx c) {
        String target=n.getPath();boolean decision="REQUEST_DECIDED".equals(n.getKind());
        String path=n.getAudienceRole()==Role.MANAGER?"/staff/manager/"+("INCIDENT".equals(n.getKind())?"incidents":"approvals")+"?id="+str(target):str(target);
        return map("id",id(n),"kind",n.getKind(),"title",n.getTitle(),"body",n.getBody(),"at",n.getCreatedAt(),"path",path,"audience",n.getAudienceRole(),"staffId",id(n.getRecipient()),"targetId",target,"requestId",decision?target:null,"sessionId",n.getAudienceRole()==Role.WAITER?target:null,"billId",n.getAudienceRole()==Role.CASHIER&&!decision?target:null,"orderId",n.getAudienceRole()==Role.KITCHEN&&Set.of("NEW_ORDER","PRIORITY").contains(n.getKind())?target:null,"readBy",c.readsOf(n).stream().map(r->id(r.getUser())).toList());
    }
    Map<String,Object> admin(RestaurantSettings s) {return map("users",db.all(User.class).stream().map(u->map("id",id(u),"fullName",u.getFullName(),"email",u.getEmail(),"role",u.getRole(),"status",u.isEnabled()?"ACTIVE":"LOCKED","createdAt",u.getCreatedAt(),"initials",u.getFullName().substring(0,1).toUpperCase())).toList(),"restaurant",map("name",s.getName(),"description",str(s.getDescription()),"address",str(s.getAddress()),"hotline",str(s.getPhone()),"email",str(s.getEmail()),"openingHours",str(s.getOpeningHours()),"facebook",str(s.getFacebook()),"instagram",str(s.getInstagram()),"tiktok",str(s.getTiktok()),"logoUrl",str(s.getLogoUrl()),"coverUrl",str(s.getCoverUrl())),"settings",map("onlineReservations",s.isOnlineReservations(),"tableHoldMinutes",s.getHoldMinutes(),"noShowMinutes",s.getNoShowMinutes(),"bufferMinutes",s.getBufferMinutes(),"billing",map("serviceChargePercent",s.getServiceChargeRate(),"vatPercent",s.getVatRate(),"bankAccount",str(s.getBankAccountNumber())),"timezone",s.getTimezone(),"currency",s.getCurrency(),"emailNotifications",s.isEmailNotifications()),"auditLogs",db.all(AuditLog.class).stream().filter(a->Set.of("ROLE_CHANGED","ACCOUNT_LOCKED","ACCOUNT_UNLOCKED","RESTAURANT_UPDATED").contains(a.getAction())).map(a->map("id",id(a),"at",a.getCreatedAt(),"actor",a.getActor()==null?"System":a.getActor().getFullName(),"action",a.getAction(),"target",a.getDescription(),"result","SUCCESS")).toList());}
}
