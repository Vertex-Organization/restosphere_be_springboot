package com.vertex.restosphere.dto.request;

import com.vertex.restosphere.common.enums.MenuItemStatus;
import jakarta.validation.constraints.NotNull;

/** Chi AVAILABLE/SOLD_OUT hop le; DISCONTINUED bi service tu choi 400 nhu lenh cu. */
public record KitchenMenuStatusRequest(@NotNull MenuItemStatus status) {}
