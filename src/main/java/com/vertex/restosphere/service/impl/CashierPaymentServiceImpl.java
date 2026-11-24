package com.vertex.restosphere.service.impl;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.common.util.EmailUtils;
import com.vertex.restosphere.common.util.Values;
import com.vertex.restosphere.dto.request.CashierDepositCashRequest;
import com.vertex.restosphere.dto.request.CashierDepositTransferRequest;
import com.vertex.restosphere.dto.request.CashierManualTransferRequest;
import com.vertex.restosphere.dto.request.CashierPayCashRequest;
import com.vertex.restosphere.dto.request.CashierPayosLinkRequest;
import com.vertex.restosphere.dto.response.CashierPaymentResponse;
import com.vertex.restosphere.dto.response.CashierPendingDepositResponse;
import com.vertex.restosphere.entity.Bill;
import com.vertex.restosphere.entity.CashierShift;
import com.vertex.restosphere.entity.Employee;
import com.vertex.restosphere.entity.OrderItem;
import com.vertex.restosphere.entity.Payment;
import com.vertex.restosphere.entity.Reservation;
import com.vertex.restosphere.entity.RestaurantSettings;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.repository.CashierShiftRepository;
import com.vertex.restosphere.repository.EmployeeRepository;
import com.vertex.restosphere.repository.PaymentRepository;
import com.vertex.restosphere.repository.ReservationRepository;
import com.vertex.restosphere.repository.RestaurantSettingsRepository;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.security.FailureLimiter;
import com.vertex.restosphere.service.BillingWorkflow;
import com.vertex.restosphere.service.CashierPaymentService;
import com.vertex.restosphere.service.ReservationWorkflow;
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
    private static final Set<OrderItem.Status> UNSERVED =
            Set.of(
                    OrderItem.Status.DRAFT,
                    OrderItem.Status.SUBMITTED,
                    OrderItem.Status.PREPARING,
                    OrderItem.Status.READY);

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
    private final AuditRecorder auditRecorder;
    private final PayosLinks payosLinks;
    private final ReservationRepository reservationRepo;
    private final ReservationWorkflow reservations;
    private final FailureLimiter approvalFailures = new FailureLimiter(10, 15 * 60_000L);

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
        Employee cashier = currentUser.require(actorId, Role.ADMIN, Role.CASHIER);
        Employee manager =
                manualTransferApprover(
                        cashier, "BILL", billId, req.managerEmail(), req.managerPassword());
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
        Outcome outcome =
                create(
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
        if (outcome.created()) {
            workflow.audit(
                    manager,
                    "manual-transfer-confirmed",
                    billId,
                    "BILL",
                    "Quản lý xác nhận chuyển khoản thủ công tại quầy, mã giao dịch " + ref);
        }
        return outcome;
    }

    // The manager types their own credentials at the counter; the cashier never sees them.
    private Employee manualTransferApprover(
            Employee cashier,
            String targetType,
            UUID targetId,
            String managerEmail,
            String managerPassword) {
        String key = EmailUtils.normalize(managerEmail);
        if (approvalFailures.blocked(key)) {
            auditApproval(cashier, targetType, targetId, "manual-transfer-blocked", key);
            throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Nhập sai quá nhiều lần. Vui lòng thử lại sau 15 phút.");
        }
        Employee manager =
                employees
                        .findByEmail(key)
                        .filter(Employee::isEnabled)
                        .filter(e -> e.getRole() == Role.MANAGER || e.getRole() == Role.ADMIN)
                        .filter(e -> passwordEncoder.matches(managerPassword, e.getPasswordHash()))
                        .orElse(null);
        if (manager == null) {
            approvalFailures.fail(key);
            auditApproval(cashier, targetType, targetId, "manual-transfer-denied", key);
            throw new ApiException(
                    HttpStatus.FORBIDDEN, "Thông tin xác nhận của quản lý không đúng.");
        }
        approvalFailures.clear(key);
        return manager;
    }

    private void auditApproval(
            Employee cashier,
            String targetType,
            UUID targetId,
            String action,
            String managerEmail) {
        auditRecorder.record(
                cashier,
                action,
                targetType,
                targetId,
                "Xác nhận chuyển khoản thủ công không thành công với tài khoản quản lý "
                        + managerEmail);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Transactional(readOnly = true)
    @Override
    public List<CashierPendingDepositResponse> pendingDeposits(UUID actorId) {
        currentUser.require(actorId, Role.ADMIN, Role.CASHIER);
        return reservationRepo.findAwaitingCashDeposit(Instant.now()).stream()
                .map(CashierPendingDepositResponse::from)
                .toList();
    }

    @Transactional
    @Override
    public Outcome depositCash(UUID actorId, UUID reservationId, CashierDepositCashRequest req) {
        return createDeposit(
                actorId,
                reservationId,
                Payment.Method.CASH,
                req.tendered(),
                req.idempotencyKey(),
                null);
    }

    @Transactional
    @Override
    public Outcome depositManualTransfer(
            UUID actorId, UUID reservationId, CashierDepositTransferRequest req) {
        Employee cashier = currentUser.require(actorId, Role.ADMIN, Role.CASHIER);
        Employee manager =
                manualTransferApprover(
                        cashier,
                        "RESERVATION",
                        reservationId,
                        req.managerEmail(),
                        req.managerPassword());
        String ref = req.transactionReference().trim();
        if (ref.isEmpty())
            throw Values.invalid("transactionReference", "Nhập mã giao dịch ngân hàng.");
        boolean replay = payments.findByIdempotencyKey(req.idempotencyKey().trim()).isPresent();
        if (!replay && payments.existsByTransactionReference(ref)) {
            throw ApiException.conflict("Mã giao dịch ngân hàng đã được dùng cho khoản khác.");
        }
        Outcome outcome =
                createDeposit(
                        actorId,
                        reservationId,
                        Payment.Method.MANUAL_TRANSFER,
                        null,
                        req.idempotencyKey(),
                        p -> {
                            p.setTransactionReference(ref);
                            p.setProofImageUrl(blankToNull(req.proofImageUrl()));
                            p.setVerifiedBy(manager);
                        });
        if (outcome.created()) {
            workflow.audit(
                    manager,
                    "manual-transfer-confirmed",
                    reservationId,
                    "RESERVATION",
                    "Quản lý xác nhận cọc chuyển khoản thủ công, mã giao dịch " + ref);
        }
        return outcome;
    }

    // A deposit is whatever the reservation asks for, never the client's number: the cashier only
    // says how it was paid. It lands on the reservation, not on a bill, until the guest checks in.
    private Outcome createDeposit(
            UUID actorId,
            UUID reservationId,
            Payment.Method method,
            BigDecimal rawTendered,
            String rawKey,
            Consumer<Payment> extra) {
        Employee user = currentUser.require(actorId, Role.ADMIN, Role.CASHIER);
        restaurantLock.acquire();
        String key = rawKey.trim();
        if (key.isEmpty() || key.length() > 150) {
            throw Values.invalid("idempotencyKey is required (max 150)");
        }
        var old = payments.findByIdempotencyKey(key);
        if (old.isPresent()) {
            Payment p = old.get();
            if (p.getReservation() == null
                    || !p.getReservation().getId().equals(reservationId)
                    || p.getMethod() != method
                    || p.getCreatedBy() == null
                    || !user.getId().equals(p.getCreatedBy().getId())) {
                throw ApiException.conflict("Idempotency key reused with different payment");
            }
            return new Outcome(CashierPaymentResponse.from(p), false);
        }
        Reservation reservation = store.get(Reservation.class, reservationId.toString());
        if (reservation.getStatus() != Reservation.Status.PENDING
                || !reservation.awaitingDeposit()) {
            throw ApiException.conflict("Đặt bàn này không còn chờ thu cọc.");
        }
        if (method == Payment.Method.CASH && reservation.getDepositCashRequestedAt() == null) {
            // Cash only arrives through reception handing the guest over; keeps one clear queue.
            throw ApiException.conflict("Lễ tân chưa chuyển đặt bàn này sang thu ngân.");
        }
        CashierShift shift =
                shifts.findFirstByCashierIdAndClosedAtIsNull(user.getId())
                        .orElseThrow(() -> ApiException.conflict("Open a cashier shift first"));
        BigDecimal amount = reservation.getDepositAmount();
        // The guest may not also pay the old QR: drop it before taking the money.
        payosLinks.cancelDepositLink(reservation, "Đã thu cọc tại quầy");
        Payment payment = new Payment();
        payment.setReservation(reservation);
        payment.setCashierShift(shift);
        payment.setAmount(amount);
        payment.setCreatedBy(user);
        payment.setIdempotencyKey(key);
        payment.setMethod(method);
        payment.setStatus(Payment.Status.COMPLETED);
        payment.setPaidAt(Instant.now());
        if (method == Payment.Method.CASH) {
            BigDecimal tendered = Values.money(rawTendered, "tendered");
            if (tendered.compareTo(amount) < 0) {
                throw Values.invalid("Insufficient cash tendered");
            }
            payment.setTendered(tendered);
        }
        if (extra != null) extra.accept(payment);
        store.save(payment);
        reservation.getPayments().add(payment);
        store.flush();
        reservations.confirmAfterDeposit(reservation);
        writes.commit(
                user, method == Payment.Method.CASH ? "deposit-cash" : "deposit-manual-transfer");
        store.flush();
        return new Outcome(CashierPaymentResponse.from(payment), true);
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
        Bill bill = payableBill(billId, billVersion);
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
        billing.notifyPaid(payment.getBill());
        store.flush();
        return new Outcome(CashierPaymentResponse.from(payment), true);
    }

    // Loads the bill and checks it can take money now: current version, check requested, all
    // served.
    private Bill payableBill(UUID billId, Long billVersion) {
        Bill bill = store.get(Bill.class, billId.toString());
        billing.requireVersion(bill, billVersion);
        billing.recalculate(bill);
        if (bill.getStatus() != Bill.Status.OPEN || !bill.awaitingPayment()) {
            throw ApiException.conflict("Bill is not awaiting payment");
        }
        if (billing.items(bill).stream().anyMatch(i -> UNSERVED.contains(i.getStatus()))) {
            throw ApiException.conflict("Unserved dishes remain");
        }
        return bill;
    }

    @Transactional
    @Override
    public Outcome createPayosLink(UUID actorId, UUID billId, CashierPayosLinkRequest req) {
        Employee user = currentUser.require(actorId, Role.ADMIN, Role.CASHIER);
        restaurantLock.acquire();
        Bill bill = payableBill(billId, req.billVersion());
        BigDecimal outstanding = bill.getTotal().subtract(billing.received(bill));
        if (outstanding.signum() <= 0) throw ApiException.conflict("Bill is already paid");
        CashierShift shift =
                shifts.findFirstByCashierIdAndClosedAtIsNull(user.getId())
                        .orElseThrow(() -> ApiException.conflict("Open a cashier shift first"));
        var pending =
                payments.findByBillIdAndMethodAndStatus(
                        bill.getId(), Payment.Method.PAYOS, Payment.Status.PENDING);
        var current =
                pending.stream().filter(p -> p.getAmount().compareTo(outstanding) == 0).findFirst();
        // Printing again for the same amount reuses the live link instead of making a second QR.
        if (current.isPresent())
            return new Outcome(CashierPaymentResponse.from(current.get()), false);
        billing.cancelPayosLinks(bill, "Hóa đơn thay đổi");
        Payment payment = payosLinks.createForBill(bill, outstanding, shift, user);
        writes.commit(user, "create-payos-link");
        store.flush();
        return new Outcome(CashierPaymentResponse.from(payment), true);
    }
}
