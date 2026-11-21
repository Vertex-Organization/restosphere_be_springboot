package com.vertex.restosphere.dto.response;

import com.vertex.restosphere.common.enums.MenuItemStatus;
import com.vertex.restosphere.entity.ApprovalRequest;
import java.time.Instant;
import java.util.UUID;

public record KitchenMenuRequestResponse(
        UUID id,
        UUID menuItemId,
        MenuItemStatus requested,
        UUID requestedBy,
        Instant requestedAt,
        ApprovalRequest.Status status,
        Instant decidedAt,
        UUID decidedBy) {}
