package com.vertex.restosphere.service.impl;

import static com.vertex.restosphere.common.util.Values.id;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.entity.Payment;
import com.vertex.restosphere.entity.Reservation;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.repository.PaymentRepository;
import com.vertex.restosphere.service.BillingWorkflow;
import com.vertex.restosphere.service.PayosGateway;
import com.vertex.restosphere.service.PayosPayments;
import com.vertex.restosphere.service.ReservationWorkflow;
import com.vertex.restosphere.service.RestaurantLock;
import com.vertex.restosphere.service.ServiceWorkflow;
import java.time.Instant;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class PayosPaymentsImpl implements PayosPayments {
    private static final String LATE_MARK = "payOS báo đã nhận tiền sau khi payment bị hủy";
    private static final Set<Reservation.Status> ALIVE =
            Set.of(Reservation.Status.PENDING, Reservation.Status.CONFIRMED);

    private final PaymentRepository payments;
    private final PayosGateway gateway;
    private final BillingWorkflow billing;
    private final ReservationWorkflow reservations;
    private final ServiceWorkflow events;
    private final RestaurantLock restaurantLock;

    @Override
    public Outcome handleWebhook(Object body) {
        var event = gateway.verifyWebhook(body);
        if (!event.success()) return Outcome.STILL_PENDING;
        return markPaid(event.orderCode(), event.amount(), event.reference());
    }

    @Override
    public Outcome simulatePaid(long orderCode) {
        if (!(gateway instanceof FakePayosGateway fake)) {
            throw ApiException.conflict("Chỉ giả lập được khi PAYOS_FAKE=true.");
        }
        var payment =
                payments.findByPayosOrderCode(orderCode)
                        .orElseThrow(() -> ApiException.notFound("Payment"));
        fake.markPaid(orderCode);
        return markPaid(orderCode, payment.getAmount().longValueExact(), "FAKE-" + orderCode);
    }

    @Override
    public Outcome markPaid(long orderCode, long amount, String reference) {
        restaurantLock.acquire();
        var found = payments.findByPayosOrderCode(orderCode);
        if (found.isEmpty()) return Outcome.UNKNOWN;
        Payment payment = found.get();
        return switch (payment.getStatus()) {
            case COMPLETED -> Outcome.ALREADY_COMPLETED;
            case PENDING -> complete(payment, amount, reference);
            default -> lateMoney(payment, amount, reference);
        };
    }

    @Override
    public Outcome refresh(Payment payment) {
        if (payment.getPayosOrderCode() == null) return Outcome.UNKNOWN;
        if (payment.getStatus() == Payment.Status.COMPLETED) return Outcome.ALREADY_COMPLETED;
        var info = gateway.status(payment.getPayosOrderCode());
        return switch (info.status()) {
            case PAID ->
                    markPaid(
                            payment.getPayosOrderCode(),
                            info.amountPaid(),
                            payment.getTransactionReference());
            case CANCELLED, EXPIRED, FAILED -> close(payment, info.status());
            case PENDING -> Outcome.STILL_PENDING;
        };
    }

    private Outcome complete(Payment payment, long amount, String reference) {
        if (payment.getAmount().longValueExact() != amount) {
            // Do not guess: a short or over payment needs a person to look at the bank statement.
            events.notify(
                    Role.MANAGER,
                    "PAYOS_AMOUNT_MISMATCH",
                    "Số tiền payOS không khớp",
                    "Mã đơn "
                            + payment.getPayosOrderCode()
                            + ": cần "
                            + payment.getAmount()
                            + ", nhận "
                            + amount
                            + ".",
                    id(payment));
            return Outcome.AMOUNT_MISMATCH;
        }
        payment.setStatus(Payment.Status.COMPLETED);
        payment.setPaidAt(Instant.now());
        if (reference != null
                && !reference.isBlank()
                && !payments.existsByTransactionReference(reference)) {
            payment.setTransactionReference(reference);
        }
        if (payment.getBill() != null) {
            billing.settle(payment.getBill());
            billing.notifyPaid(payment.getBill());
        }
        if (payment.getReservation() != null) depositPaid(payment.getReservation());
        return Outcome.COMPLETED;
    }

    private void depositPaid(Reservation reservation) {
        if (ALIVE.contains(reservation.getStatus())) {
            reservations.confirmAfterDeposit(reservation);
            return;
        }
        events.notify(
                Role.MANAGER,
                "LATE_DEPOSIT",
                "Cọc về cho đặt bàn đã đóng",
                reservation.getCode() + " · " + reservation.getCustomerName(),
                id(reservation));
    }

    // The link was cancelled on our side but the guest still paid: never revive the payment.
    private Outcome lateMoney(Payment payment, long amount, String reference) {
        String note = payment.getNote() == null ? "" : payment.getNote();
        if (!note.contains(LATE_MARK)) {
            payment.setNote(
                    (note.isEmpty() ? "" : note + " | ")
                            + LATE_MARK
                            + " ("
                            + amount
                            + (reference == null ? "" : ", " + reference)
                            + ").");
            events.notify(
                    Role.MANAGER,
                    "LATE_PAYMENT",
                    "Nhận tiền cho link payOS đã hủy",
                    "Mã đơn "
                            + payment.getPayosOrderCode()
                            + ", "
                            + amount
                            + " đ: hãy đối soát và xử lý thủ công.",
                    id(payment));
        }
        return Outcome.LATE_PAYMENT;
    }

    private Outcome close(Payment payment, PayosGateway.LinkStatus status) {
        if (payment.getStatus() == Payment.Status.PENDING) {
            payment.setStatus(
                    status == PayosGateway.LinkStatus.FAILED
                            ? Payment.Status.FAILED
                            : Payment.Status.CANCELLED);
        }
        return Outcome.CLOSED;
    }
}
