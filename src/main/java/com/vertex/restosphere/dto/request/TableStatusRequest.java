package com.vertex.restosphere.dto.request;

import com.vertex.restosphere.common.enums.TableStatus;
import jakarta.validation.constraints.NotNull;

public record TableStatusRequest(@NotNull TableStatus status) {
}
