package com.vertex.restosphere.dto.response;

import com.vertex.restosphere.entity.ApprovalRequest;
import java.time.Instant;
import java.util.UUID;

public record KitchenMenuRequestResponse(UUID id, UUID menuItemId, String requested, UUID requestedBy,
        Instant requestedAt, ApprovalRequest.Status status, Instant decidedAt, UUID decidedBy) {}
