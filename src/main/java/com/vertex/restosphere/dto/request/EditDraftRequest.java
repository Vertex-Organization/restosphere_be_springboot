package com.vertex.restosphere.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Updates the quantity / note of a cart line. */
public record EditDraftRequest(@NotNull @Positive BigDecimal quantity, @Size(max = 1000) String note) {
}
