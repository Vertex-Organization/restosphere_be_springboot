package com.vertex.restosphere.dto.request;

import com.vertex.restosphere.common.enums.AccountStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(@NotNull AccountStatus status) {
}
