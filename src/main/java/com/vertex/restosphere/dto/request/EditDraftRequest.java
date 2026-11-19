package com.vertex.restosphere.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Sửa số lượng / ghi chú một dòng trong giỏ. */
public record EditDraftRequest(@NotNull @Positive BigDecimal quantity, @Size(max = 1000) String note) {
}
