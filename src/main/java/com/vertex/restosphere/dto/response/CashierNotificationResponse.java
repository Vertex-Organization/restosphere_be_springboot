package com.vertex.restosphere.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Matches the FE CashierNotification type; readBy lists every staff member who read it. */
public record CashierNotificationResponse(UUID id, String kind, String title, String body, Instant at, String billId,
        String requestId, List<UUID> readBy) {}
