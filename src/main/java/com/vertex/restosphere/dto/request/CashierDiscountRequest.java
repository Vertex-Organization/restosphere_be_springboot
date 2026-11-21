package com.vertex.restosphere.dto.request;

import com.vertex.restosphere.common.enums.DiscountMode;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CashierDiscountRequest(@NotNull Long billVersion, @NotNull Discount discount) {
    public record Discount(DiscountMode mode, BigDecimal value, String reason) {}
}
