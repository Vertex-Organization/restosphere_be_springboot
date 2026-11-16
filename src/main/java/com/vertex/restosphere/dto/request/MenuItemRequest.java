package com.vertex.restosphere.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record MenuItemRequest(
        @NotBlank @Size(max = 150) String name,
        @NotNull UUID categoryId,
        @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 0) BigDecimal price,
        @Size(max = 1000) String description,
        @Size(max = 500) String imageUrl) {
}
