package com.vertex.restosphere.dto.request;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/** Một dòng món cần tách; quantity để trống nghĩa là chuyển cả dòng. */
public record SplitItemRequest(@NotNull UUID itemId, BigDecimal quantity) {}
