package com.vertex.restosphere.dto.request;

import com.vertex.restosphere.common.enums.MenuItemStatus;
import jakarta.validation.constraints.NotNull;

public record MenuItemStatusRequest(@NotNull MenuItemStatus status) {}
