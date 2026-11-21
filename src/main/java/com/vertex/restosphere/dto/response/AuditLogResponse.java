package com.vertex.restosphere.dto.response;

import com.vertex.restosphere.entity.AuditLog;
import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID id, Instant at, String actor, String action, String target, String result) {

    public static AuditLogResponse from(AuditLog a) {
        return new AuditLogResponse(
                a.getId(),
                a.getCreatedAt(),
                a.getActor() == null ? "System" : a.getActor().getFullName(),
                a.getAction(),
                a.getDescription(),
                "SUCCESS");
    }
}
