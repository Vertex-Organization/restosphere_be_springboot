package com.vertex.restosphere.dto.response;

import com.vertex.restosphere.entity.AuditLog;
import com.vertex.restosphere.entity.User;
import java.time.Instant;
import java.util.UUID;

public record ManagerActivityResponse(
        UUID id,
        Instant at,
        UUID actorId,
        String actorName,
        UUID entityId,
        String action,
        String text) {

    public static ManagerActivityResponse from(AuditLog a) {
        User actor = a.getActor();
        return new ManagerActivityResponse(
                a.getId(),
                a.getCreatedAt(),
                actor == null ? null : actor.getId(),
                actor == null ? null : actor.getFullName(),
                a.getTargetId(),
                a.getAction(),
                a.getDescription());
    }
}
