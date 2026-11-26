package com.vertex.restosphere.dto.response;

import com.vertex.restosphere.common.util.Values;
import com.vertex.restosphere.entity.Payment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CashierPaymentResponse(
        UUID id,
        String code,
        UUID billId,
        UUID reservationId,
        UUID shiftId,
        Payment.Method method,
        String status,
        BigDecimal amount,
        BigDecimal tendered,
        BigDecimal change,
        String transferContent,
        String bankReference,
        String proofImageUrl,
        String checkoutUrl,
        String qrCode,
        Long orderCode,
        String verifiedBy,
        String createdBy,
        Instant createdAt,
        Instant completedAt) {
    public static CashierPaymentResponse from(Payment p) {
        return new CashierPaymentResponse(
                p.getId(),
                Values.code("P", p),
                p.getBill() == null ? null : p.getBill().getId(),
                p.getReservation() == null ? null : p.getReservation().getId(),
                p.getCashierShift() == null ? null : p.getCashierShift().getId(),
                p.getMethod(),
                p.getStatus() == Payment.Status.COMPLETED ? "SUCCEEDED" : p.getStatus().name(),
                p.getAmount(),
                p.getTendered(),
                p.getTendered() == null ? null : p.getTendered().subtract(p.getAmount()),
                p.getMethod() == Payment.Method.MANUAL_TRANSFER
                        ? "RS" + p.getId().toString().replace("-", "")
                        : null,
                p.getTransactionReference(),
                p.getProofImageUrl(),
                p.getCheckoutUrl(),
                p.getQrCode(),
                p.getPayosOrderCode(),
                Values.id(p.getVerifiedBy()),
                Values.id(p.getCreatedBy()),
                p.getCreatedAt(),
                p.getPaidAt());
    }
}
