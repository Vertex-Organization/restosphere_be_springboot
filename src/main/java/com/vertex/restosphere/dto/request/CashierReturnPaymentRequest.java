package com.vertex.restosphere.dto.request;

import jakarta.validation.constraints.NotNull;

public record CashierReturnPaymentRequest(@NotNull Long billVersion, String reason) {}
