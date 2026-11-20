package com.vertex.restosphere.dto.request;

import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record ManagerReadNotificationsRequest(@Size(max = 500) List<UUID> ids) {
}
