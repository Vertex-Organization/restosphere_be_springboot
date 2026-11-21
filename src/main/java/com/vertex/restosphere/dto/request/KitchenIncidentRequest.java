package com.vertex.restosphere.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KitchenIncidentRequest(
        @NotBlank String type,
        @NotBlank String severity,
        @NotBlank String description,
        @Size(max = 100) String orderId,
        @Size(max = 100) String itemId) {}
