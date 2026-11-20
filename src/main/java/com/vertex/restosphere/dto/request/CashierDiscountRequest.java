package com.vertex.restosphere.dto.request;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CashierDiscountRequest(@NotNull Long billVersion, @NotNull Discount discount) {
    public record Discount(String mode, BigDecimal value, String reason) {}
}
