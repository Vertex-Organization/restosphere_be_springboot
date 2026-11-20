package com.vertex.restosphere.dto.response;

import com.vertex.restosphere.entity.OrderItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CashierItemResponse(UUID id, UUID orderId, UUID menuItemId, String name, BigDecimal unitPrice,
        BigDecimal quantity, String note, OrderItem.Status status, Instant servedAt, String cancellationReason) {
    public static CashierItemResponse from(OrderItem i) {
        return new CashierItemResponse(i.getId(), i.getOrder().getId(), i.getMenuItem().getId(), i.getItemName(),
                i.getUnitPrice(), i.getQuantity(), i.getNote() == null ? "" : i.getNote(), i.getStatus(),
                i.getServedAt(), i.getRejectionReason());
    }
}
