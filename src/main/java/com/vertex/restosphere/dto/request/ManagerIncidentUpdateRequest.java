package com.vertex.restosphere.dto.request;

import com.vertex.restosphere.entity.Incident;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** assigneeId null = giu nguoi xu ly hien tai. reply toi thieu 5 ky tu sau khi cat: kiem tra o service. */
public record ManagerIncidentUpdateRequest(UUID assigneeId, @NotNull Incident.Status status,
        @Size(max = 2000) String reply) {
}
