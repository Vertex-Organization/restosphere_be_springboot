package com.vertex.restosphere.service.impl;

import static com.vertex.restosphere.common.util.Values.whole;

import com.vertex.restosphere.common.enums.DiscountMode;
import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.common.enums.TableStatus;
import com.vertex.restosphere.entity.*;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.service.BillingWorkflow;
import com.vertex.restosphere.service.WorkspaceStore;
import java.math.*;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BillingWorkflowImpl implements BillingWorkflow {
    private final WorkspaceStore db;

    @Override
    public Bill open() {
        var bill = new Bill();
        bill.setCode("B-" + UUID.randomUUID().toString().substring(28).toUpperCase());
        return db.save(bill);
    }

    @Override
    public Bill bill(TableSession session) {
        return session.getBill();
    }

    @Override
    public List<OrderItem> items(Bill bill) {
        return db.query(OrderItem.class, "from OrderItem i where i.order.session.bill=?1", bill);
    }

    @Override
    public void recalculate(Bill b) {
        if (b.getStatus() != Bill.Status.OPEN) return;
        var subtotal =
                items(b).stream()
                        .filter(i -> !OrderItem.NOT_BILLABLE.contains(i.getStatus()))
                        .map(i -> whole(i.getUnitPrice().multiply(i.getQuantity())))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discount =
                b.getDiscountValue() == null
                        ? BigDecimal.ZERO
                        : b.getDiscountMode() == DiscountMode.PERCENT
                                ? whole(subtotal.multiply(b.getDiscountValue()).movePointLeft(2))
                                : b.getDiscountValue();
        discount = discount.min(subtotal);
        var settings = db.lock();
        var net = subtotal.subtract(discount);
        var service = whole(net.multiply(settings.getServiceChargeRate()).movePointLeft(2));
        var tax = whole(net.add(service).multiply(settings.getVatRate()).movePointLeft(2));
        b.setSubtotal(subtotal);
        b.setDiscount(discount);
        b.setServiceCharge(service);
        b.setTaxAmount(tax);
        b.setTotal(net.add(service).add(tax));
    }

    @Override
    public BigDecimal received(Bill b) {
        return db
                .query(
                        Payment.class,
                        "from Payment p where p.bill=?1 and p.status=?2",
                        b,
                        Payment.Status.COMPLETED)
                .stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public void editable(Bill b) {
        if (b.getStatus() != Bill.Status.OPEN
                || !db.query(
                                Payment.class,
                                "from Payment p where p.bill=?1 and p.reservation is null and p.status in ?2",
                                b,
                                List.of(Payment.Status.PENDING, Payment.Status.COMPLETED))
                        .isEmpty()) throw ApiException.conflict("Bill has payments or is closed");
    }

    @Override
    public void requireVersion(Bill b, long version) {
        if (version != b.getVersion())
            throw ApiException.conflict("Bill changed; refresh before retrying");
    }

    @Override
    public void applyDiscount(
            Bill b, DiscountMode mode, BigDecimal value, String reason, Employee user) {
        b.setDiscountMode(mode);
        b.setDiscountValue(value);
        b.setDiscountReason(reason);
        b.setDiscountAppliedBy(user);
        b.setDiscountAppliedAt(Instant.now());
        recalculate(b);
    }

    @Override
    public void settle(Bill b) {
        if (received(b).compareTo(b.getTotal()) >= 0) {
            b.setStatus(Bill.Status.PAID);
            b.setPaidAt(Instant.now());
            closeSessions(b);
        } else {
            b.setStatus(Bill.Status.OPEN);
            b.setPaidAt(null);
        }
    }

    // TODO(confirm): tables go straight to AVAILABLE on payment; the old guest-left/clean steps no
    // longer apply (mobile finish screen to be updated in phase 3).
    private void closeSessions(Bill b) {
        var now = Instant.now();
        for (var s : b.getSessions()) {
            if (s.getStatus() == TableSession.Status.CLOSED) continue;
            s.setStatus(TableSession.Status.CLOSED);
            s.setGuestLeftAt(now);
            s.setEndedAt(now.isBefore(s.getStartedAt()) ? s.getStartedAt() : now);
            s.getTable().setStatus(TableStatus.AVAILABLE);
            completeReservationIfDone(s.getReservation());
        }
    }

    private void completeReservationIfDone(Reservation r) {
        if (r == null || r.getStatus() != Reservation.Status.SEATED) return;
        db.flush();
        boolean stillOpen =
                !db.query(
                                TableSession.class,
                                "from TableSession s where s.reservation=?1 and s.status<>?2",
                                r,
                                TableSession.Status.CLOSED)
                        .isEmpty();
        if (!stillOpen) r.setStatus(Reservation.Status.COMPLETED);
    }

    // Undo closeSessions after a voided payment; only possible while no one else took the tables.
    private void reopenSessions(Bill b) {
        for (var s : b.getSessions()) {
            if (s.getStatus() != TableSession.Status.CLOSED) continue;
            var table = s.getTable();
            boolean taken =
                    table.getStatus() != TableStatus.AVAILABLE
                            || !db.query(
                                            TableSession.class,
                                            "from TableSession o where o.table=?1 and o.status<>?2",
                                            table,
                                            TableSession.Status.CLOSED)
                                    .isEmpty();
            if (taken) throw ApiException.conflict("Payment can no longer be voided");
        }
        for (var s : b.getSessions()) {
            if (s.getStatus() != TableSession.Status.CLOSED) continue;
            s.setStatus(TableSession.Status.PAYMENT_REQUESTED);
            s.setEndedAt(null);
            s.setGuestLeftAt(null);
            s.getTable().setStatus(TableStatus.OCCUPIED);
            if (s.getReservation() != null
                    && s.getReservation().getStatus() == Reservation.Status.COMPLETED) {
                s.getReservation().setStatus(Reservation.Status.SEATED);
            }
        }
    }

    @Override
    public ApprovalRequest request(
            ApprovalRequest.Type type,
            String target,
            UUID targetId,
            BigDecimal amount,
            String reason,
            Employee user) {
        if (!db.query(
                        ApprovalRequest.class,
                        "from ApprovalRequest r where r.type=?1 and r.targetId=?2 and r.status=?3",
                        type,
                        targetId,
                        ApprovalRequest.Status.PENDING)
                .isEmpty()) throw ApiException.conflict("A request is already pending");
        var r = new ApprovalRequest();
        r.setType(type);
        r.setTargetType(target);
        r.setTargetId(targetId);
        r.setAmount(amount);
        r.setReason(reason);
        r.setRequestedBy(user);
        db.save(r);
        if (type != ApprovalRequest.Type.RESERVATION) {
            var n = new Notification();
            n.setAudienceRole(Role.MANAGER);
            n.setKind("APPROVAL");
            n.setTitle("Có yêu cầu phê duyệt mới");
            n.setBody(reason.length() > 1900 ? reason.substring(0, 1900) : reason);
            n.setPath(r.getId().toString());
            db.save(n);
        }
        return r;
    }

    @Override
    public void decide(UUID requestId, boolean approve, String reason, Employee user) {
        var r = db.get(ApprovalRequest.class, requestId.toString());
        if (r.getStatus() != ApprovalRequest.Status.PENDING)
            throw ApiException.conflict("Request already decided");
        if (r.getRequestedBy().equals(user))
            throw ApiException.conflict("Cannot approve your own request");
        if (approve
                && user.getRole() == Role.MANAGER
                && r.getAmount() != null
                && r.getAmount().compareTo(BigDecimal.valueOf(db.lock().getManagerApprovalLimit()))
                        > 0) throw ApiException.conflict("Approval exceeds manager limit");
        if (approve)
            switch (r.getType()) {
                case DISCOUNT -> {
                    var b = db.get(Bill.class, r.getTargetId().toString());
                    editable(b);
                    recalculate(b);
                    applyDiscount(
                            b, r.getDiscountMode(), r.getDiscountValue(), r.getReason(), user);
                    b.setApprovalRequest(r);
                }
                case CANCEL_ITEM, BILL_ADJUSTMENT -> {
                    var item = db.get(OrderItem.class, r.getTargetId().toString());
                    var b = bill(item.getOrder().getSession());
                    editable(b);
                    if (Set.of(
                                    OrderItem.Status.CANCELLED,
                                    OrderItem.Status.REJECTED,
                                    OrderItem.Status.VOIDED)
                            .contains(item.getStatus()))
                        throw ApiException.conflict("Line already removed");
                    item.setStatus(
                            r.getType() == ApprovalRequest.Type.CANCEL_ITEM
                                    ? OrderItem.Status.CANCELLED
                                    : OrderItem.Status.VOIDED);
                    item.setRejectionReason(r.getReason());
                    recalculate(b);
                    if (r.getType() == ApprovalRequest.Type.CANCEL_ITEM) {
                        var k = new Notification();
                        k.setAudienceRole(Role.KITCHEN);
                        k.setKind("ITEM_CANCELLED");
                        k.setTitle("Món đã bị hủy");
                        k.setBody(item.getItemName());
                        k.setPath(item.getOrder().getId().toString());
                        db.save(k);
                    }
                }
                case PAYMENT_VOID -> {
                    var p = db.get(Payment.class, r.getTargetId().toString());
                    if (p.getStatus() != Payment.Status.COMPLETED
                            || p.getBill() == null
                            || p.getReservation() != null
                            || p.getCashierShift() == null
                            || p.getCashierShift().getClosedAt() != null)
                        throw ApiException.conflict("Payment can no longer be voided");
                    reopenSessions(p.getBill());
                    p.setStatus(Payment.Status.VOIDED);
                    p.getBill().setStatus(Bill.Status.OPEN);
                    p.getBill().setPaidAt(null);
                }
                case MENU_STATUS ->
                        db.get(MenuItem.class, r.getTargetId().toString())
                                .setStatus(r.getMenuStatus());
                case RESERVATION -> {
                    var reservation = db.get(Reservation.class, r.getTargetId().toString());
                    if (reservation.getStatus() == Reservation.Status.PENDING
                            && !db.query(
                                            ReservationTable.class,
                                            "from ReservationTable a where a.reservation=?1",
                                            reservation)
                                    .isEmpty()) reservation.setStatus(Reservation.Status.CONFIRMED);
                }
            }
        r.setStatus(approve ? ApprovalRequest.Status.APPROVED : ApprovalRequest.Status.REJECTED);
        r.setDecidedBy(user);
        r.setDecidedAt(Instant.now());
        r.setDecisionNote(reason);
    }
}
