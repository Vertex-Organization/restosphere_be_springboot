package com.vertex.restosphere.service;

import static com.vertex.restosphere.service.WorkspaceStore.*;
import static com.vertex.restosphere.service.ReservationWorkflow.*;
import com.vertex.restosphere.entity.*;
import com.vertex.restosphere.exception.ApiException;
import java.math.*;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BillingWorkflow {
    private final WorkspaceStore db;
    public Bill bill(TableSession session) {return db.all(Bill.class).stream().filter(b->b.getSession().equals(session)).findFirst().orElseThrow(()->ApiException.notFound("Bill"));}
    public List<OrderItem> items(Bill bill) {return db.all(OrderItem.class).stream().filter(i->i.getOrder().getSession().equals(bill.getSession())).toList();}
    public static BigDecimal whole(BigDecimal n) {return n.setScale(0,RoundingMode.HALF_UP);}
    public static BigDecimal money(Map<String,Object> c,String key) {var n=number(c,key);if(n.signum()<0||n.scale()>0||n.compareTo(new BigDecimal("999999999999"))>0)throw invalid("Invalid whole-dong amount: "+key);return n;}
    public void recalculate(Bill b) {
        if(b.getStatus()!=Bill.Status.OPEN)return;
        var subtotal=items(b).stream().filter(i->!Set.of(OrderItem.Status.CANCELLED,OrderItem.Status.REJECTED,OrderItem.Status.VOIDED).contains(i.getStatus())).map(i->whole(i.getUnitPrice().multiply(i.getQuantity()))).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal discount=b.getDiscountValue()==null?BigDecimal.ZERO:"PERCENT".equals(b.getDiscountMode())?whole(subtotal.multiply(b.getDiscountValue()).movePointLeft(2)):b.getDiscountValue();
        discount=discount.min(subtotal);var settings=db.lock();var net=subtotal.subtract(discount);
        var service=whole(net.multiply(settings.getServiceChargeRate()).movePointLeft(2));var tax=whole(net.add(service).multiply(settings.getVatRate()).movePointLeft(2));
        b.setSubtotal(subtotal);b.setDiscount(discount);b.setServiceCharge(service);b.setTaxAmount(tax);b.setTotal(net.add(service).add(tax));
    }
    public BigDecimal received(Bill b) {return db.all(Payment.class).stream().filter(p->p.getBill().equals(b)&&p.getStatus()==Payment.Status.COMPLETED).map(Payment::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);}
    void editable(Bill b) {if(b.getStatus()!=Bill.Status.OPEN||db.all(Payment.class).stream().anyMatch(p->p.getBill().equals(b)&&Set.of(Payment.Status.PENDING,Payment.Status.COMPLETED).contains(p.getStatus())))throw ApiException.conflict("Bill has payments or is closed");}
    void version(Bill b,Map<String,Object> c) {if(number(c,"billVersion").compareTo(BigDecimal.valueOf(b.getVersion()))!=0)throw ApiException.conflict("Bill changed; refresh before retrying");}
    public String command(String type,Map<String,Object> c,User user) {
        if(type.equals("open-shift")&&!text(c,"scheduledShiftId").isEmpty()) {
            var scheduled=db.get(Shift.class,text(c,"scheduledShiftId"));
            if(db.all(ShiftAssignment.class).stream().noneMatch(a->a.getShift().equals(scheduled)&&a.getUser().equals(user)))throw invalid("Scheduled shift is not assigned to this cashier");
            if(Instant.now().isBefore(scheduled.getStartsAt().minusSeconds(1800))||Instant.now().isAfter(scheduled.getEndsAt()))throw ApiException.conflict("Outside scheduled shift window");
        }
        switch(type) {
            case "open-shift" -> {if(db.all(CashierShift.class).stream().anyMatch(s->s.getCashier().equals(user)&&s.getClosedAt()==null))throw ApiException.conflict("A shift is already open");var s=new CashierShift();s.setCashier(user);s.setOpenedAt(Instant.now());s.setOpeningCash(money(c,"openingFloat"));if(!text(c,"scheduledShiftId").isEmpty())s.setScheduledShift(db.get(Shift.class,text(c,"scheduledShiftId")));db.save(s);return id(s);}
            case "close-shift" -> {var s=db.get(CashierShift.class,required(c,"shiftId",100));if(!s.getCashier().equals(user)||s.getClosedAt()!=null)throw ApiException.conflict("Shift is not open for this cashier");if(db.all(Payment.class).stream().anyMatch(p->p.getCashierShift().equals(s)&&p.getStatus()==Payment.Status.PENDING))throw ApiException.conflict("Resolve pending transfers first");s.setExpectedCash(s.getOpeningCash().add(db.all(Payment.class).stream().filter(p->p.getCashierShift().equals(s)&&p.getMethod()==Payment.Method.CASH&&p.getStatus()==Payment.Status.COMPLETED).map(Payment::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add)));s.setCountedCash(money(c,"countedCash"));s.setNote(text(c,"note"));s.setClosedAt(Instant.now());return id(s);}
            case "confirm-transfer", "cancel-transfer" -> {
                var p=db.get(Payment.class,required(c,"paymentId",100));if(p.getMethod()!=Payment.Method.TRANSFER||p.getStatus()!=Payment.Status.PENDING)throw ApiException.conflict("Transfer is not pending");
                if(!p.getCashierShift().getCashier().equals(user)||p.getCashierShift().getClosedAt()!=null)throw ApiException.conflict("Transfer belongs to another or closed shift");
                if(type.equals("cancel-transfer"))p.setStatus(Payment.Status.CANCELLED);
                else {String ref=required(c,"bankReference",150);if(db.all(Payment.class).stream().anyMatch(x->ref.equals(x.getTransactionReference())))throw ApiException.conflict("Bank reference was already used");p.setTransactionReference(ref);p.setVerifiedBy(id(user));p.setStatus(Payment.Status.COMPLETED);p.setPaidAt(Instant.now());settle(p.getBill());}return id(p);
            }
            case "request-void" -> {var p=db.get(Payment.class,required(c,"paymentId",100));if(p.getStatus()!=Payment.Status.COMPLETED)throw ApiException.conflict("Only successful payments may be voided");return id(request(ApprovalRequest.Type.PAYMENT_VOID,"PAYMENT",p.getId(),p.getAmount(),required(c,"reason",1000),user));}
            case "request-adjustment" -> {var b=db.get(Bill.class,required(c,"billId",100));editable(b);var item=db.get(OrderItem.class,required(c,"itemId",100));if(!item.getOrder().getSession().equals(b.getSession())||Set.of(OrderItem.Status.CANCELLED,OrderItem.Status.REJECTED,OrderItem.Status.VOIDED).contains(item.getStatus()))throw invalid("Invalid bill line");return id(request(ApprovalRequest.Type.BILL_ADJUSTMENT,"ORDER_ITEM",item.getId(),whole(item.getUnitPrice().multiply(item.getQuantity())),required(c,"reason",1000),user));}
            case "withdraw-request" -> {var r=db.get(ApprovalRequest.class,required(c,"requestId",100));if(!r.getRequestedBy().equals(user)||r.getStatus()!=ApprovalRequest.Status.PENDING)throw ApiException.conflict("Request cannot be withdrawn");r.setStatus(ApprovalRequest.Status.WITHDRAWN);return id(r);}
        }
        if(type.equals("pay-cash")||type.equals("create-transfer")) {
            String key=required(c,"idempotencyKey",150);
            var old=db.all(Payment.class).stream().filter(p->key.equals(p.getIdempotencyKey())).findFirst();
            if(old.isPresent()) {var p=old.get();if(!id(user).equals(p.getCreatedBy())||!id(p.getBill()).equals(text(c,"billId"))||p.getAmount().compareTo(money(c,"amount"))!=0||!p.getMethod().name().equals(type.equals("pay-cash")?"CASH":"TRANSFER")||(type.equals("pay-cash")&&p.getTendered().compareTo(money(c,"tendered"))!=0))throw ApiException.conflict("Idempotency key reused with different payment");return id(p);}
        }
        var b=db.get(Bill.class,required(c,"billId",100));version(b,c);recalculate(b);
        switch(type) {
            case "apply-discount", "request-discount" -> {
                editable(b);var d=object(c,"discount");String mode=required(d,"mode",20);var value=money(d,"value");if(!Set.of("PERCENT","AMOUNT").contains(mode)||value.signum()==0||(mode.equals("PERCENT")&&value.compareTo(BigDecimal.valueOf(100))>0))throw invalid("Invalid discount");var amount=mode.equals("PERCENT")?whole(b.getSubtotal().multiply(value).movePointLeft(2)):value;if(amount.compareTo(b.getSubtotal())>0)throw invalid("Discount exceeds subtotal");String reason=required(d,"reason",1000);
                if(type.equals("request-discount")) {var r=request(ApprovalRequest.Type.DISCOUNT,"BILL",b.getId(),amount,reason,user);r.setDiscountMode(mode);r.setDiscountValue(value);return id(r);}
                if(amount.compareTo(whole(b.getSubtotal().multiply(new BigDecimal("0.10"))))>0)throw ApiException.conflict("Discount requires manager approval");applyDiscount(b,mode,value,reason,user);return id(b);
            }
            case "remove-discount" -> {editable(b);b.setDiscountValue(null);b.setDiscountMode(null);recalculate(b);return id(b);}
            case "return-payment-request" -> {editable(b);required(c,"reason",1000);if(b.getSession().getStatus()!=TableSession.Status.PAYMENT_REQUESTED)throw ApiException.conflict("Payment was not requested");b.getSession().setStatus(TableSession.Status.OPEN);b.setRequestedAt(null);return id(b);}
            case "pay-cash", "create-transfer" -> {
                if(b.getStatus()!=Bill.Status.OPEN||b.getSession().getStatus()!=TableSession.Status.PAYMENT_REQUESTED)throw ApiException.conflict("Bill is not awaiting payment");
                if(items(b).stream().anyMatch(i->Set.of(OrderItem.Status.SUBMITTED,OrderItem.Status.PREPARING,OrderItem.Status.READY).contains(i.getStatus())))throw ApiException.conflict("Unserved dishes remain");
                var amount=money(c,"amount");if(amount.signum()<=0||amount.compareTo(b.getTotal().subtract(received(b)))>0)throw invalid("Payment exceeds outstanding amount");
                if(db.all(Payment.class).stream().anyMatch(p->p.getBill().equals(b)&&p.getStatus()==Payment.Status.PENDING))throw ApiException.conflict("A transfer is pending");
                var shift=db.all(CashierShift.class).stream().filter(s->s.getCashier().equals(user)&&s.getClosedAt()==null).findFirst().orElseThrow(()->ApiException.conflict("Open a cashier shift first"));
                var p=new Payment();p.setBill(b);p.setCashierShift(shift);p.setAmount(amount);p.setCreatedBy(id(user));p.setIdempotencyKey(required(c,"idempotencyKey",150));
                if(type.equals("pay-cash")) {var tendered=money(c,"tendered");if(tendered.compareTo(amount)<0)throw invalid("Insufficient cash tendered");p.setTendered(tendered);p.setMethod(Payment.Method.CASH);p.setStatus(Payment.Status.COMPLETED);p.setPaidAt(Instant.now());}
                else {if(db.lock().getBankAccountNumber()==null||db.lock().getBankAccountNumber().isBlank())throw ApiException.conflict("Bank account is not configured");p.setMethod(Payment.Method.TRANSFER);}
                db.save(p);db.flush();settle(b);return id(p);
            }
            default -> throw invalid("Unsupported cashier command");
        }
    }
    public void applyDiscount(Bill b,String mode,BigDecimal value,String reason,User user) {b.setDiscountMode(mode);b.setDiscountValue(value);b.setDiscountReason(reason);b.setDiscountAppliedBy(id(user));b.setDiscountAppliedAt(Instant.now());recalculate(b);}
    void settle(Bill b) {if(received(b).compareTo(b.getTotal())>=0) {b.setStatus(Bill.Status.PAID);b.setPaidAt(Instant.now());} else {b.setStatus(Bill.Status.OPEN);b.setPaidAt(null);}}
    public ApprovalRequest request(ApprovalRequest.Type type,String target,UUID targetId,BigDecimal amount,String reason,User user) {
        if(db.all(ApprovalRequest.class).stream().anyMatch(r->r.getType()==type&&r.getTargetId().equals(targetId)&&r.getStatus()==ApprovalRequest.Status.PENDING))throw ApiException.conflict("A request is already pending");
        var r=new ApprovalRequest();r.setType(type);r.setTargetType(target);r.setTargetId(targetId);r.setAmount(amount);r.setReason(reason);r.setRequestedBy(user);return db.save(r);
    }
    public void decide(Map<String,Object> c,User user) {
        var r=db.get(ApprovalRequest.class,required(c,"id",100));if(r.getStatus()!=ApprovalRequest.Status.PENDING)throw ApiException.conflict("Request already decided");if(r.getRequestedBy().equals(user))throw ApiException.conflict("Cannot approve your own request");String reason=required(c,"reason",1000);
        if(!(c.get("approve") instanceof Boolean))throw invalid("approve must be boolean");
        if(Boolean.TRUE.equals(c.get("approve")))switch(r.getType()) {
            case DISCOUNT -> {var b=db.get(Bill.class,r.getTargetId().toString());editable(b);recalculate(b);applyDiscount(b,r.getDiscountMode(),r.getDiscountValue(),r.getReason(),user);}
            case CANCEL_ITEM, BILL_ADJUSTMENT -> {var item=db.get(OrderItem.class,r.getTargetId().toString());var b=bill(item.getOrder().getSession());editable(b);if(Set.of(OrderItem.Status.CANCELLED,OrderItem.Status.REJECTED,OrderItem.Status.VOIDED).contains(item.getStatus()))throw ApiException.conflict("Line already removed");item.setStatus(r.getType()==ApprovalRequest.Type.CANCEL_ITEM?OrderItem.Status.CANCELLED:OrderItem.Status.VOIDED);item.setRejectionReason(r.getReason());recalculate(b);}
            case PAYMENT_VOID -> {var p=db.get(Payment.class,r.getTargetId().toString());if(p.getStatus()!=Payment.Status.COMPLETED||p.getCashierShift().getClosedAt()!=null||p.getBill().getSession().getGuestLeftAt()!=null)throw ApiException.conflict("Payment can no longer be voided");p.setStatus(Payment.Status.VOIDED);p.getBill().setStatus(Bill.Status.OPEN);p.getBill().setPaidAt(null);}
            case MENU_STATUS -> db.get(MenuItem.class,r.getTargetId().toString()).setStatus(com.vertex.restosphere.common.enums.MenuItemStatus.valueOf(r.getMenuStatus()));
            case RESERVATION -> {var reservation=db.get(Reservation.class,r.getTargetId().toString());if(reservation.getStatus()!=Reservation.Status.PENDING)throw ApiException.conflict("Reservation is no longer pending");reservation.setStatus(Reservation.Status.CONFIRMED);}
        }
        r.setStatus(Boolean.TRUE.equals(c.get("approve"))?ApprovalRequest.Status.APPROVED:ApprovalRequest.Status.REJECTED);r.setDecidedBy(user);r.setDecidedAt(Instant.now());r.setDecisionNote(reason);
    }
    public Map<String,Object> view(Bill b) {
        return map("id",id(b),"code",b.getCode(),"sessionId",id(b.getSession()),"status",b.getStatus()==Bill.Status.PAID?"PAID":b.getSession().getStatus()==TableSession.Status.PAYMENT_REQUESTED?"PAYMENT_REQUESTED":"UNPAID","version",b.getVersion(),"createdAt",b.getCreatedAt(),"requestedAt",b.getRequestedAt(),"paidAt",b.getPaidAt(),"amount",b.getTotal(),"currentAmount",b.getTotal(),"requestedAmount",b.getRequestedAt()==null?null:b.getTotal(),"discount",b.getDiscountValue()==null?null:map("mode",b.getDiscountMode(),"value",b.getDiscountValue(),"reason",b.getDiscountReason(),"appliedBy",b.getDiscountAppliedBy(),"appliedAt",b.getDiscountAppliedAt()),"totals",map("subtotal",b.getSubtotal(),"discount",b.getDiscount(),"serviceCharge",b.getServiceCharge(),"tax",b.getTaxAmount(),"total",b.getTotal()),"history",db.all(AuditLog.class).stream().filter(a->b.getId().equals(a.getTargetId())).map(a->map("at",a.getCreatedAt(),"by",id(a.getActor()),"text",a.getDescription())).toList());
    }
    public Map<String,Object> payment(Payment p) {return map("id",id(p),"code",code("P",p),"billId",id(p.getBill()),"shiftId",id(p.getCashierShift()),"method",p.getMethod(),"status",p.getStatus()==Payment.Status.COMPLETED?"SUCCEEDED":p.getStatus().name(),"amount",p.getAmount(),"tendered",p.getTendered(),"change",p.getTendered()==null?null:p.getTendered().subtract(p.getAmount()),"transferContent",p.getMethod()==Payment.Method.TRANSFER?"RS"+id(p).replace("-",""):null,"bankReference",p.getTransactionReference(),"verifiedBy",p.getVerifiedBy(),"createdBy",p.getCreatedBy(),"createdAt",p.getCreatedAt(),"completedAt",p.getPaidAt());}
}
