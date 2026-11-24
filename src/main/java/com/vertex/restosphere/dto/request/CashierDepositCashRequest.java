package com.vertex.restosphere.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Thu cọc tiền mặt tại quầy thu ngân; số tiền cọc tính ở máy chủ, chỉ nhận tiền khách đưa. */
public record CashierDepositCashRequest(
        @NotNull BigDecimal tendered, @NotBlank String idempotencyKey) {}
