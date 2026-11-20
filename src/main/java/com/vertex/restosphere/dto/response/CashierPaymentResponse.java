package com.vertex.restosphere.dto.response;

import com.vertex.restosphere.entity.Payment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Matches the FE Payment type (COMPLETED is exposed as SUCCEEDED). */
public record CashierPaymentResponse(UUID id, String code, UUID billId, UUID shiftId, Payment.Method method,
        String status, BigDecimal amount, BigDecimal tendered, BigDecimal change, String transferContent,
        String bankReference, String verifiedBy, String createdBy, Instant createdAt, Instant completedAt) {

    public static CashierPaymentResponse from(Payment p) {
        return new CashierPaymentResponse(p.getId(), "P-" + p.getId(), p.getBill().getId(),
                p.getCashierShift().getId(), p.getMethod(),
                p.getStatus() == Payment.Status.COMPLETED ? "SUCCEEDED" : p.getStatus().name(), p.getAmount(),
                p.getTendered(), p.getTendered() == null ? null : p.getTendered().subtract(p.getAmount()),
                p.getMethod() == Payment.Method.TRANSFER ? "RS" + p.getId().toString().replace("-", "") : null,
                p.getTransactionReference(), p.getVerifiedBy(), p.getCreatedBy(), p.getCreatedAt(), p.getPaidAt());
    }
}
