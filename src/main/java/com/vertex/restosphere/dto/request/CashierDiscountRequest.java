package com.vertex.restosphere.dto.request;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Used by apply (PUT discount) and request (POST discount-requests). Fields are validated in the service. */
public record CashierDiscountRequest(@NotNull Long billVersion, @NotNull Discount discount) {

    /** mode is PERCENT or AMOUNT; value is a percent (1-100) or a whole-dong amount. */
    public record Discount(String mode, BigDecimal value, String reason) {}
}
