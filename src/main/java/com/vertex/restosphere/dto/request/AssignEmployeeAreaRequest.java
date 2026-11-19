package com.vertex.restosphere.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssignEmployeeAreaRequest(@NotNull UUID areaId) {
}
