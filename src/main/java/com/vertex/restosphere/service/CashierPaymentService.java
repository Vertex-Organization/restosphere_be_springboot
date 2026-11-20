package com.vertex.restosphere.service;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.dto.request.CashierPayCashRequest;
import com.vertex.restosphere.dto.response.CashierPaymentResponse;
import com.vertex.restosphere.entity.Bill;
import com.vertex.restosphere.entity.CashierShift;
import com.vertex.restosphere.entity.OrderItem;
import com.vertex.restosphere.entity.Payment;
import com.vertex.restosphere.entity.TableSession;
import com.vertex.restosphere.entity.User;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.repository.CashierShiftRepository;
import com.vertex.restosphere.repository.PaymentRepository;
import com.vertex.restosphere.security.CurrentUserService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payments (REST). Cash payment follows the legacy pay-cash command step by step and reuses the BillingWorkflow
 * helpers (recalculate, received, items, settle, version) so money rules live in one place.
 */
@Service
@RequiredArgsConstructor
public class CashierPaymentService {

    /** created = false when an idempotent retry returned the earlier payment. */
    public record Outcome(CashierPaymentResponse payment, boolean created) {}

    private final CurrentUserService currentUser;
    private final RestaurantLock restaurantLock;
    private final WorkspaceStore store;
    private final BillingWorkflow billing;
    private final CashierWriteSupport writes;
    private final PaymentRepository payments;
    private final CashierShiftRepository shifts;

    @Transactional
    public Outcome payCash(UUID actorId, UUID billId, CashierPayCashRequest req) {
        User user = currentUser.require(actorId, Role.ADMIN, Role.CASHIER);
        restaurantLock.acquire();
        String key = req.idempotencyKey().trim();
        if (key.isEmpty() || key.length() > 150) {
            throw WorkspaceStore.invalid("idempotencyKey is required (max 150)");
        }
        var old = payments.findByIdempotencyKey(key);
        if (old.isPresent()) {
            Payment p = old.get();
            if (!user.getId().toString().equals(p.getCreatedBy()) || !p.getBill().getId().equals(billId)
                    || p.getAmount().compareTo(CashierWriteSupport.money(req.amount(), "amount")) != 0
                    || p.getMethod() != Payment.Method.CASH
                    || p.getTendered().compareTo(CashierWriteSupport.money(req.tendered(), "tendered")) != 0) {
                throw ApiException.conflict("Idempotency key reused with different payment");
            }
            return new Outcome(CashierPaymentResponse.from(p), false);
        }
        Bill bill = store.get(Bill.class, billId.toString());
        billing.version(bill, Map.of("billVersion", req.billVersion()));
        billing.recalculate(bill);
        if (bill.getStatus() != Bill.Status.OPEN
                || bill.getSession().getStatus() != TableSession.Status.PAYMENT_REQUESTED) {
            throw ApiException.conflict("Bill is not awaiting payment");
        }
        if (billing.items(bill).stream().anyMatch(i -> Set.of(OrderItem.Status.SUBMITTED,
                OrderItem.Status.PREPARING, OrderItem.Status.READY).contains(i.getStatus()))) {
            throw ApiException.conflict("Unserved dishes remain");
        }
        BigDecimal amount = CashierWriteSupport.money(req.amount(), "amount");
        if (amount.signum() <= 0 || amount.compareTo(bill.getTotal().subtract(billing.received(bill))) > 0) {
            throw WorkspaceStore.invalid("Payment exceeds outstanding amount");
        }
        if (payments.existsByBillIdAndStatus(bill.getId(), Payment.Status.PENDING)) {
            throw ApiException.conflict("A transfer is pending");
        }
        CashierShift shift = shifts.findFirstByCashierIdAndClosedAtIsNull(user.getId())
                .orElseThrow(() -> ApiException.conflict("Open a cashier shift first"));
        BigDecimal tendered = CashierWriteSupport.money(req.tendered(), "tendered");
        if (tendered.compareTo(amount) < 0) {
            throw WorkspaceStore.invalid("Insufficient cash tendered");
        }
        Payment payment = new Payment();
        payment.setBill(bill);
        payment.setCashierShift(shift);
        payment.setAmount(amount);
        payment.setCreatedBy(user.getId().toString());
        payment.setIdempotencyKey(key);
        payment.setTendered(tendered);
        payment.setMethod(Payment.Method.CASH);
        payment.setStatus(Payment.Status.COMPLETED);
        payment.setPaidAt(Instant.now());
        store.save(payment);
        store.flush();
        billing.settle(bill);
        writes.commit(user, "pay-cash");
        writes.notifyIfPaid(payment);
        store.flush();
        return new Outcome(CashierPaymentResponse.from(payment), true);
    }
}
