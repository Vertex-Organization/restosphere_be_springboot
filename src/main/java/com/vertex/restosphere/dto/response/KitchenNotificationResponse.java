package com.vertex.restosphere.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record KitchenNotificationResponse(UUID id, String kind, String title, String body, Instant at, String path,
        String orderId, List<UUID> readBy) {}
