package com.vertex.restosphere.dto.request;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CashierOpenShiftRequest(@NotNull BigDecimal openingFloat, String scheduledShiftId) {}
