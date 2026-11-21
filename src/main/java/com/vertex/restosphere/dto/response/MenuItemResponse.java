package com.vertex.restosphere.dto.response;

import com.vertex.restosphere.common.enums.MenuItemStatus;
import com.vertex.restosphere.common.enums.PriceUnit;
import com.vertex.restosphere.entity.MenuItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MenuItemResponse(
        UUID id,
        String name,
        UUID categoryId,
        String categoryName,
        BigDecimal price,
        String description,
        String imageUrl,
        MenuItemStatus status,
        PriceUnit unit,
        Instant updatedAt) {

    public static MenuItemResponse from(MenuItem i) {
        return new MenuItemResponse(
                i.getId(),
                i.getName(),
                i.getCategory().getId(),
                i.getCategory().getName(),
                i.getPrice(),
                i.getDescription(),
                i.getImageUrl(),
                i.getStatus(),
                i.getPriceUnit(),
                i.getUpdatedAt());
    }
}
