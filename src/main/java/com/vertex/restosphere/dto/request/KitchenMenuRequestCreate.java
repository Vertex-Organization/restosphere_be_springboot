package com.vertex.restosphere.dto.request;

import com.vertex.restosphere.common.enums.MenuItemStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record KitchenMenuRequestCreate(@NotBlank @Size(max = 100) String menuItemId, @NotNull MenuItemStatus status) {}
