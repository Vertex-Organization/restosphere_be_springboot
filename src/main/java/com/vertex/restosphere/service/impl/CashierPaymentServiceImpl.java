package com.vertex.restosphere.service.impl;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.common.util.Values;
import com.vertex.restosphere.dto.request.CashierConfirmTransferRequest;
import com.vertex.restosphere.dto.request.CashierCreateTransferRequest;
import com.vertex.restosphere.dto.request.CashierPayCashRequest;
import com.vertex.restosphere.dto.response.CashierPaymentResponse;
import com.vertex.restosphere.entity.Bill;
import com.vertex.restosphere.entity.CashierShift;
import com.vertex.restosphere.entity.Employee;
import com.vertex.restosphere.entity.OrderItem;
import com.vertex.restosphere.entity.Payment;
import com.vertex.restosphere.entity.RestaurantSettings;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.repository.CashierShiftRepository;
import com.vertex.restosphere.repository.PaymentRepository;
import com.vertex.restosphere.repository.RestaurantSettingsRepository;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.BillingWorkflow;
import com.vertex.restosphere.service.CashierPaymentService;
import com.vertex.restosphere.service.RestaurantLock;
import com.vertex.restosphere.service.ServiceWorkflow;
import com.vertex.restosphere.service.WorkspaceCommit;
import com.vertex.restosphere.service.WorkspaceStore;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CashierPaymentServiceImpl implements CashierPaymentService {
    private final CurrentUserService currentUser;
    private final RestaurantLock restaurantLock;
    private final WorkspaceStore store;
    private final BillingWorkflow billing;
    private final WorkspaceCommit writes;
    private final ServiceWorkflow workflow;
    private final PaymentRepository payments;
    private final CashierShiftRepository shifts;
    private final RestaurantSettingsRepository settings;

    @Transactional(readOnly = true)
    @Override
    public List<CashierPaymentResponse> history(
            UUID actorId, Instant from, Instant to, Payment.Method method, String status) {
        currentUser.require(actorId, Role.ADMIN, Role.CASHIER);
        Payment.Status wanted = null;
        if (status != null && !status.isBlank()) {
            String name = status.trim().toUpperCase();
            try {
                wanted = Payment.Status.valueOf(name.equals("SUCCEEDED") ? "COMPLETED" : name);
            } catch (IllegalArgumentException e) {
                throw Values.invalid("Invalid status");
            }
        }
        return payments
                .search(
                        from == null ? Instant.EPOCH : from,
                        to == null ? Instant.parse("9999-12-31T00:00:00Z") : to,
                        method == null ? List.of(Payment.Method.values()) : List.of(method),
                        wanted == null ? List.of(Payment.Status.values()) : List.of(wanted))
                .stream()
                .map(CashierPaymentResponse::from)
                .toList();
    }

    @Transactional
    @Override
    public Outcome payCash(UUID actorId, UUID billId, CashierPayCashRequest req) {
        return create(
                actorId,
                billId,
                Payment.Method.CASH,
                req.billVersion(),
                req.amount(),
                req.tendered(),
                req.idempotencyKey());
    }

    @Transactional
    @Override
    public Outcome createTransfer(UUID actorId, UUID billId, CashierCreateTransferRequest req) {
        return create(
                actorId,
                billId,
                Payment.Method.TRANSFER,
                req.billVersion(),
                req.amount(),
                null,
                req.idempotencyKey());
    }

    @Transactional
    @Override
    public CashierPaymentResponse confirmTransfer(
            UUID actorId, UUID paymentId, CashierConfirmTransferRequest req) {
        Employee user = currentUser.require(actorId, Role.ADMIN, Role.CASHIER);
        restaurantLock.acquire();
        Payment payment = pendingTransferOf(user, paymentId);
        String ref = req.bankReference() == null ? "" : req.bankReference().trim();
        if (ref.isEmpty() || ref.length() > 150) {
            throw Values.invalid("bankReference is required (max 150)");
        }
        if (payments.existsByTransactionReference(ref)) {
            throw ApiException.conflict("Bank reference was already used");
        }
        payment.setTransactionReference(ref);
        payment.setVerifiedBy(user);
        payment.setStatus(Payment.Status.COMPLETED);
        payment.setPaidAt(Instant.now());
        billing.settle(payment.getBill());
        writes.commit(user, "confirm-transfer");
        notifyIfPaid(payment);
        store.flush();
        return CashierPaymentResponse.from(payment);
    }

    @Transactional
    @Override
    public CashierPaymentResponse cancelTransfer(UUID actorId, UUID paymentId) {
        Employee user = currentUser.require(actorId, Role.ADMIN, Role.CASHIER);
        restaurantLock.acquire();
        Payment payment = pendingTransferOf(user, paymentId);
        payment.setStatus(Payment.Status.CANCELLED);
        writes.commit(user, "cancel-transfer");
        store.flush();
        return CashierPaymentResponse.from(payment);
    }

    private Payment pendingTransferOf(Employee user, UUID paymentId) {
        Payment p = store.get(Payment.class, paymentId.toString());
        if (p.getMethod() != Payment.Method.TRANSFER || p.getStatus() != Payment.Status.PENDING) {
            throw ApiException.conflict("Transfer is not pending");
        }
        if (!p.getCashierShift().getCashier().getId().equals(user.getId())
                || p.getCashierShift().getClosedAt() != null) {
            throw ApiException.conflict("Transfer belongs to another or closed shift");
        }
        return p;
    }

    private Outcome create(
            UUID actorId,
            UUID billId,
            Payment.Method method,
            Long billVersion,
            BigDecimal rawAmount,
            BigDecimal rawTendered,
            String rawKey) {
        boolean cash = method == Payment.Method.CASH;
        Employee user = currentUser.require(actorId, Role.ADMIN, Role.CASHIER);
        restaurantLock.acquire();
        String key = rawKey.trim();
        if (key.isEmpty() || key.length() > 150) {
            throw Values.invalid("idempotencyKey is required (max 150)");
        }
        var old = payments.findByIdempotencyKey(key);
        if (old.isPresent()) {
            Payment p = old.get();
            if (p.getCreatedBy() == null
                    || !user.getId().equals(p.getCreatedBy().getId())
                    || !p.getBill().getId().equals(billId)
                    || p.getAmount().compareTo(Values.money(rawAmount, "amount")) != 0
                    || p.getMethod() != method
                    || (cash
                            && p.getTendered().compareTo(Values.money(rawTendered, "tendered"))
                                    != 0)) {
                throw ApiException.conflict("Idempotency key reused with different payment");
            }
            return new Outcome(CashierPaymentResponse.from(p), false);
        }
        Bill bill = store.get(Bill.class, billId.toString());
        billing.requireVersion(bill, billVersion);
        billing.recalculate(bill);
        if (bill.getStatus() != Bill.Status.OPEN || !bill.awaitingPayment()) {
            throw ApiException.conflict("Bill is not awaiting payment");
        }
        if (billing.items(bill).stream()
                .anyMatch(
                        i ->
                                Set.of(
                                                OrderItem.Status.DRAFT,
                                                OrderItem.Status.SUBMITTED,
                                                OrderItem.Status.PREPARING,
                                                OrderItem.Status.READY)
                                        .contains(i.getStatus()))) {
            throw ApiException.conflict("Unserved dishes remain");
        }
        BigDecimal amount = Values.money(rawAmount, "amount");
        if (amount.signum() <= 0
                || amount.compareTo(bill.getTotal().subtract(billing.received(bill))) > 0) {
            throw Values.invalid("Payment exceeds outstanding amount");
        }
        if (payments.existsByBillIdAndStatus(bill.getId(), Payment.Status.PENDING)) {
            throw ApiException.conflict("A transfer is pending");
        }
        CashierShift shift =
                shifts.findFirstByCashierIdAndClosedAtIsNull(user.getId())
                        .orElseThrow(() -> ApiException.conflict("Open a cashier shift first"));
        Payment payment = new Payment();
        payment.setBill(bill);
        payment.setCashierShift(shift);
        payment.setAmount(amount);
        payment.setCreatedBy(user);
        payment.setIdempotencyKey(key);
        if (cash) {
            BigDecimal tendered = Values.money(rawTendered, "tendered");
            if (tendered.compareTo(amount) < 0) {
                throw Values.invalid("Insufficient cash tendered");
            }
            payment.setTendered(tendered);
            payment.setMethod(Payment.Method.CASH);
            payment.setStatus(Payment.Status.COMPLETED);
            payment.setPaidAt(Instant.now());
        } else {
            RestaurantSettings cfg = settings.findBySingletonKey(1).orElseGet(store::lock);
            if (cfg.getBankAccountNumber() == null || cfg.getBankAccountNumber().isBlank()) {
                throw ApiException.conflict("Bank account is not configured");
            }
            payment.setMethod(Payment.Method.TRANSFER);
        }
        store.save(payment);
        store.flush();
        billing.settle(bill);
        writes.commit(user, cash ? "pay-cash" : "create-transfer");
        if (cash) {
            notifyIfPaid(payment);
        }
        store.flush();
        return new Outcome(CashierPaymentResponse.from(payment), true);
    }

    private void notifyIfPaid(Payment payment) {
        Bill bill = payment.getBill();
        if (bill.getStatus() == Bill.Status.PAID) {
            workflow.notify(
                    Role.WAITER,
                    "PAYMENT",
                    "Payment received",
                    bill.getCode(),
                    bill.primarySession().getId().toString());
        }
    }
}
