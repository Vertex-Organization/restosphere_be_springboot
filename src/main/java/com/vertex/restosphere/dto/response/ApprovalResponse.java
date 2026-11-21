package com.vertex.restosphere.dto.response;

import com.vertex.restosphere.entity.ApprovalRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ApprovalResponse(
        UUID id,
        String code,
        ApprovalRequest.Type type,
        ApprovalRequest.Status status,
        String targetType,
        UUID targetId,
        String targetLabel,
        UUID billId,
        UUID paymentId,
        UUID itemId,
        BigDecimal amount,
        String menuStatus,
        String reason,
        UUID requestedBy,
        Instant requestedAt,
        UUID decidedBy,
        Instant decidedAt,
        String decisionNote) {}
