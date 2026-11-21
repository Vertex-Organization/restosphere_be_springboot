package com.vertex.restosphere.dto.response;

import com.vertex.restosphere.entity.Payment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ManagerPaymentResponse(
        UUID id, UUID billId, Instant at, Payment.Method method, BigDecimal amount, String status) {

    public static ManagerPaymentResponse from(Payment p) {
        String status =
                switch (p.getStatus()) {
                    case COMPLETED -> "COMPLETED";
                    case PENDING -> "PENDING";
                    default -> "FAILED";
                };
        return new ManagerPaymentResponse(
                p.getId(),
                p.getBill().getId(),
                p.getPaidAt() == null ? p.getCreatedAt() : p.getPaidAt(),
                p.getMethod(),
                p.getAmount(),
                status);
    }
}
