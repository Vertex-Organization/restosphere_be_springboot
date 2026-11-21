package com.vertex.restosphere.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vertex.restosphere.entity.ApprovalRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CashierRequestResponse(
        UUID id,
        String code,
        ApprovalRequest.Type type,
        ApprovalRequest.Status status,
        UUID billId,
        String targetType,
        UUID targetId,
        BigDecimal amount,
        UUID paymentId,
        UUID itemId,
        Discount discount,
        String reason,
        UUID requestedBy,
        Instant requestedAt,
        UUID decidedBy,
        Instant decidedAt,
        String decisionNote) {
    public record Discount(String mode, BigDecimal value, BigDecimal amount) {}

    public static CashierRequestResponse from(
            ApprovalRequest r, UUID billId, UUID paymentId, UUID itemId) {
        Discount discount =
                r.getDiscountValue() == null
                        ? null
                        : new Discount(r.getDiscountMode(), r.getDiscountValue(), r.getAmount());
        return new CashierRequestResponse(
                r.getId(),
                "R-" + r.getId(),
                r.getType(),
                r.getStatus(),
                billId,
                r.getTargetType(),
                r.getTargetId(),
                r.getAmount(),
                paymentId,
                itemId,
                discount,
                r.getReason(),
                r.getRequestedBy().getId(),
                r.getCreatedAt(),
                r.getDecidedBy() == null ? null : r.getDecidedBy().getId(),
                r.getDecidedAt(),
                r.getDecisionNote());
    }
}
