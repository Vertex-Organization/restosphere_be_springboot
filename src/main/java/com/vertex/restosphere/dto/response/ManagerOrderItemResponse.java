package com.vertex.restosphere.dto.response;

import com.vertex.restosphere.entity.Employee;
import com.vertex.restosphere.entity.OrderItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ManagerOrderItemResponse(
        UUID id,
        UUID menuItemId,
        String name,
        String unit,
        BigDecimal quantity,
        BigDecimal unitPrice,
        OrderItem.Status status,
        String note,
        UUID chefId,
        String chefName,
        Instant startedAt,
        Instant readyAt,
        Instant servedAt,
        String rejectionReason) {

    public static ManagerOrderItemResponse from(OrderItem i) {
        Employee chef = i.getChef();
        return new ManagerOrderItemResponse(
                i.getId(),
                i.getMenuItem().getId(),
                i.getItemName(),
                i.getUnit(),
                i.getQuantity(),
                i.getUnitPrice(),
                i.getStatus(),
                i.getNote(),
                chef == null ? null : chef.getId(),
                chef == null ? null : chef.getFullName(),
                i.getStartedAt(),
                i.getReadyAt(),
                i.getServedAt(),
                i.getRejectionReason());
    }
}
