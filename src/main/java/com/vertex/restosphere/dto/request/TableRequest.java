package com.vertex.restosphere.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record TableRequest(
        @NotBlank @Size(max = 30) String code,
        @NotNull UUID areaId,
        @Min(1) @Max(100) int capacity) {
}
