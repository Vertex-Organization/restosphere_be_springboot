package com.vertex.restosphere.service.impl;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.common.util.EmailUtils;
import com.vertex.restosphere.common.util.Values;
import com.vertex.restosphere.dto.request.CashierManualTransferRequest;
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
import com.vertex.restosphere.repository.EmployeeRepository;
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
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final EmployeeRepository employees;
    private final PasswordEncoder passwordEncoder;

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
    public Outcome payManualTransfer(UUID actorId, UUID billId, CashierManualTransferRequest req) {
        Employee manager = manualTransferApprover(req.managerEmail(), req.managerPassword());
        String ref = req.transactionReference().trim();
        if (ref.isEmpty())
            throw Values.invalid("transactionReference", "Nhập mã giao dịch ngân hàng.");
        boolean replay = payments.findByIdempotencyKey(req.idempotencyKey().trim()).isPresent();
        if (!replay && payments.existsByTransactionReference(ref)) {
            throw ApiException.conflict("Mã giao dịch ngân hàng đã được dùng cho khoản khác.");
        }
        RestaurantSettings cfg = settings.findBySingletonKey(1).orElseGet(store::lock);
        if (cfg.getBankAccountNumber() == null || cfg.getBankAccountNumber().isBlank()) {
            throw ApiException.conflict("Nhà hàng chưa cấu hình tài khoản ngân hàng.");
        }
        return create(
                actorId,
                billId,
                Payment.Method.MANUAL_TRANSFER,
                req.billVersion(),
                req.amount(),
                null,
                req.idempotencyKey(),
                p -> {
                    p.setTransactionReference(ref);
                    p.setProofImageUrl(blankToNull(req.proofImageUrl()));
                    p.setVerifiedBy(manager);
                });
    }

    // The manager types their own credentials at the counter; the cashier never sees them.
    private Employee manualTransferApprover(String email, String password) {
        String key = EmailUtils.normalize(email);
        Employee manager =
                employees
                        .findByEmail(key)
                        .filter(Employee::isEnabled)
                        .filter(e -> e.getRole() == Role.MANAGER || e.getRole() == Role.ADMIN)
                        .filter(e -> passwordEncoder.matches(password, e.getPasswordHash()))
                        .orElse(null);
        // shortcut: no lockout here (login has one); add the same counter if this is abused.
        if (manager == null) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN, "Thông tin xác nhận của quản lý không đúng.");
        }
        return manager;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Outcome create(
            UUID actorId,
            UUID billId,
            Payment.Method method,
            Long billVersion,
            BigDecimal rawAmount,
            BigDecimal rawTendered,
            String rawKey) {
        return create(actorId, billId, method, billVersion, rawAmount, rawTendered, rawKey, null);
    }

    private Outcome create(
            UUID actorId,
            UUID billId,
            Payment.Method method,
            Long billVersion,
            BigDecimal rawAmount,
            BigDecimal rawTendered,
            String rawKey,
            Consumer<Payment> extra) {
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
            throw ApiException.conflict("Hóa đơn đang có mã QR payOS chờ thanh toán.");
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
        payment.setMethod(method);
        payment.setStatus(Payment.Status.COMPLETED);
        payment.setPaidAt(Instant.now());
        if (cash) {
            BigDecimal tendered = Values.money(rawTendered, "tendered");
            if (tendered.compareTo(amount) < 0) {
                throw Values.invalid("Insufficient cash tendered");
            }
            payment.setTendered(tendered);
        }
        if (extra != null) extra.accept(payment);
        store.save(payment);
        store.flush();
        billing.settle(bill);
        writes.commit(user, cash ? "pay-cash" : "pay-manual-transfer");
        notifyIfPaid(payment);
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
