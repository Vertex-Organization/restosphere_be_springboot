package com.vertex.restosphere.dto.response;

import com.vertex.restosphere.entity.Incident;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record KitchenIncidentResponse(UUID id, String code, String type, Incident.Severity severity,
        Incident.Status status, String title, String description, UUID orderId, UUID itemId, UUID reporterId,
        UUID assigneeId, Instant at, List<UUID> notifiedStaffIds, Response response) {

    public record Response(UUID by, Instant at, String text) {}
}
