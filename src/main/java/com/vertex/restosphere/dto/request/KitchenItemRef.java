package com.vertex.restosphere.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record KitchenItemRef(@NotBlank String id, @NotNull Long version) {}
