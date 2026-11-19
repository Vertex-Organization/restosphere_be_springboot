package com.vertex.restosphere.dto.response;

import com.vertex.restosphere.common.enums.MenuItemStatus;
import java.time.Instant;
import java.util.UUID;

public record KitchenMenuItemResponse(UUID id, String name, UUID categoryId, String imageUrl, MenuItemStatus status,
        Instant updatedAt) {}
