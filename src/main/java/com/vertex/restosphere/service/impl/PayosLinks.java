package com.vertex.restosphere.service.impl;

import static com.vertex.restosphere.common.util.Values.id;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.config.PayosProperties;
import com.vertex.restosphere.entity.Bill;
import com.vertex.restosphere.entity.CashierShift;
import com.vertex.restosphere.entity.Employee;
import com.vertex.restosphere.entity.Payment;
import com.vertex.restosphere.entity.Reservation;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.repository.PaymentRepository;
import com.vertex.restosphere.service.PayosGateway;
import com.vertex.restosphere.service.ServiceWorkflow;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Creates and cancels payOS links and keeps the matching PENDING payment in step. Holds no business
 * rule beyond that, so billing and reservation code can both use it without depending on each
 * other.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayosLinks {
    // Milliseconds * 1000 + noise stays below the 2^53 limit payOS accepts until the year 2255.
    private static final int ORDER_CODE_NOISE = 1000;
    private static final int MAX_CODE_ATTEMPTS = 5;

    private final PayosGateway gateway;
    private final PayosProperties properties;
    private final PaymentRepository payments;
    private final ServiceWorkflow events;

    public Payment createForBill(
            Bill bill, BigDecimal amount, CashierShift shift, Employee createdBy) {
        Payment payment =
                open("TT " + bill.getCode(), amount, Instant.now().plus(properties.billLinkTtl()));
        payment.setBill(bill);
        payment.setCashierShift(shift);
        payment.setCreatedBy(createdBy);
        return payments.save(payment);
    }

    public Payment createForDeposit(
            Reservation reservation, BigDecimal amount, Instant expiresAt, Employee createdBy) {
        Payment payment = open("Coc " + reservation.getCode(), amount, expiresAt);
        payment.setReservation(reservation);
        payment.setCreatedBy(createdBy);
        reservation.getPayments().add(payment);
        return payments.save(payment);
    }

    /** The live link of a deposit if one exists, otherwise a new one for the amount still due. */
    public Payment ensureDepositLink(Reservation reservation, Employee createdBy) {
        Payment live = reservation.livePayosPayment();
        if (live != null) return live;
        if (reservation.getStatus() != Reservation.Status.PENDING
                || !reservation.awaitingDeposit()) {
            throw ApiException.conflict("Đặt bàn này không còn chờ thu cọc.");
        }
        Instant due = reservation.getDepositDueAt();
        if (due == null || !due.isAfter(Instant.now())) {
            throw ApiException.conflict("Đã quá hạn thu cọc của đặt bàn này.");
        }
        return createForDeposit(reservation, reservation.getDepositAmount(), due, createdBy);
    }

    /** Cancels the guest-payable link of a reservation that no longer needs a deposit. */
    public void cancelDepositLink(Reservation reservation, String reason) {
        Payment live = reservation.livePayosPayment();
        if (live != null) cancel(live, reason);
    }

    /**
     * Marks the payment CANCELLED first, then asks payOS to drop the link. If payOS cannot be
     * reached the link may still be payable, so a manager is told to cancel it by hand.
     */
    public void cancel(Payment payment, String reason) {
        if (payment.getStatus() != Payment.Status.PENDING) return;
        payment.setStatus(Payment.Status.CANCELLED);
        if (payment.getPayosOrderCode() == null) return;
        try {
            gateway.cancelLink(payment.getPayosOrderCode(), reason);
        } catch (ApiException e) {
            log.warn(
                    "Could not cancel payOS order {}: {}",
                    payment.getPayosOrderCode(),
                    e.getMessage());
            events.notify(
                    Role.MANAGER,
                    "PAYOS_CANCEL_FAILED",
                    "Không hủy được link payOS",
                    "Mã đơn "
                            + payment.getPayosOrderCode()
                            + ": hãy hủy link trên payOS để khách không thể trả trễ.",
                    id(payment));
        }
    }

    public void cancelAll(Collection<Payment> pending, String reason) {
        pending.forEach(p -> cancel(p, reason));
    }

    private Payment open(String description, BigDecimal amount, Instant expiresAt) {
        long orderCode = nextOrderCode();
        var link =
                gateway.createLink(
                        new PayosGateway.LinkRequest(
                                orderCode, amount.longValueExact(), description, expiresAt));
        Payment payment = new Payment();
        payment.setMethod(Payment.Method.PAYOS);
        payment.setStatus(Payment.Status.PENDING);
        payment.setAmount(amount);
        payment.setPayosOrderCode(orderCode);
        payment.setCheckoutUrl(link.checkoutUrl());
        payment.setQrCode(link.qrCode());
        return payment;
    }

    private long nextOrderCode() {
        for (int i = 0; i < MAX_CODE_ATTEMPTS; i++) {
            long code =
                    Instant.now().toEpochMilli() * ORDER_CODE_NOISE
                            + ThreadLocalRandom.current().nextInt(ORDER_CODE_NOISE);
            if (!payments.existsByPayosOrderCode(code)) return code;
        }
        throw ApiException.conflict("Không tạo được mã đơn payOS, vui lòng thử lại.");
    }
}
