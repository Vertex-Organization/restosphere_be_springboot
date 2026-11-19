package com.vertex.restosphere.dto.response;

import com.vertex.restosphere.entity.OrderItem;
import com.vertex.restosphere.entity.User;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Giong item(OrderItem) cua snapshot cu; quantity BigDecimal vi mon tinh theo kg co so le. */
public record ManagerOrderItemResponse(UUID id, UUID menuItemId, String name, String unit, BigDecimal quantity,
        BigDecimal unitPrice, OrderItem.Status status, String note, UUID chefId, String chefName,
        Instant startedAt, Instant readyAt, Instant servedAt, String rejectionReason) {

    /** Can nap san chef (OrderItemRepository.findByOrderIdIn EntityGraph). */
    public static ManagerOrderItemResponse from(OrderItem i) {
        User chef = i.getChef();
        return new ManagerOrderItemResponse(i.getId(), i.getMenuItem().getId(), i.getItemName(), i.getUnit(),
                i.getQuantity(), i.getUnitPrice(), i.getStatus(), i.getNote(), chef == null ? null : chef.getId(),
                chef == null ? null : chef.getFullName(), i.getStartedAt(), i.getReadyAt(), i.getServedAt(),
                i.getRejectionReason());
    }
}
