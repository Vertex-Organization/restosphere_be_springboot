package com.vertex.restosphere.dto.request;

import com.vertex.restosphere.common.enums.Role;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(@NotNull Role role) {}
