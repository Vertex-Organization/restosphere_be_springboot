package com.vertex.restosphere.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CashierPayCashRequest(@NotNull Long billVersion, @NotNull BigDecimal amount,
        @NotNull BigDecimal tendered, @NotBlank String idempotencyKey) {}
