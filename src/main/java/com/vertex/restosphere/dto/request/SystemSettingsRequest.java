package com.vertex.restosphere.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record SystemSettingsRequest(
        @NotNull Boolean onlineReservations,
        @NotNull @Min(5) @Max(60) Integer tableHoldMinutes,
        @NotNull @Min(5) @Max(120) Integer noShowMinutes,
        @NotNull @Min(0) @Max(60) Integer bufferMinutes,
        @NotNull @Valid Billing billing,
        @NotNull String timezone,
        @NotNull String currency,
        @NotNull Boolean emailNotifications) {

    public record Billing(
            @NotNull @DecimalMin("0") @DecimalMax("30") @Digits(integer = 3, fraction = 2) BigDecimal serviceChargePercent,
            @NotNull @DecimalMin("0") @DecimalMax("20") @Digits(integer = 3, fraction = 2) BigDecimal vatPercent,
            @Pattern(regexp = "\\s*(\\d{6,20})?\\s*", message = "must be 6-20 digits") String bankAccount,
            @Size(max = 100) String bankName,
            @Size(max = 100) String bankAccountName) {
    }
}
