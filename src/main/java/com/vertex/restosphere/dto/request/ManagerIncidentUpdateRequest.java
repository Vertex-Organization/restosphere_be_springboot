package com.vertex.restosphere.dto.request;

import com.vertex.restosphere.entity.Incident;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ManagerIncidentUpdateRequest(UUID assigneeId, @NotNull Incident.Status status,
        @Size(max = 2000) String reply) {
}
