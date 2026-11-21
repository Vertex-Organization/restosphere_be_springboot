package com.vertex.restosphere.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CashierCloseShiftRequest(
        @NotNull BigDecimal countedCash, @Size(max = 1000) String note) {}
