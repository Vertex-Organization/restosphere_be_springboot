package com.vertex.restosphere.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** targetId = Notification.path nguyen ban (id doi tuong, du lieu cu co the khong phai UUID). */
public record ManagerNotificationResponse(UUID id, String kind, String title, String body, Instant at,
        String targetId, List<UUID> readBy) {
}
