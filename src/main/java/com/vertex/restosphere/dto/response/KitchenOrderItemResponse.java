package com.vertex.restosphere.dto.response;

import com.vertex.restosphere.entity.OrderItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record KitchenOrderItemResponse(
        UUID id,
        UUID orderId,
        UUID menuItemId,
        String name,
        BigDecimal quantity,
        String note,
        OrderItem.Status status,
        long version,
        Instant submittedAt,
        Instant startedAt,
        UUID startedBy,
        Instant readyAt,
        UUID readyBy,
        Instant servedAt,
        Instant rejectedAt,
        UUID rejectedBy,
        String rejectionReason,
        List<History> history) {
    public record History(Instant at, OrderItem.Status status, UUID by, String note) {}
}
