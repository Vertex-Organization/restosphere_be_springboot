package com.vertex.restosphere.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CashierNotificationResponse(UUID id, String kind, String title, String body, Instant at, String billId,
        String requestId, List<UUID> readBy) {}
