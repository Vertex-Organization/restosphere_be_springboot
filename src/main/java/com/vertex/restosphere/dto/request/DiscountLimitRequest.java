package com.vertex.restosphere.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record DiscountLimitRequest(
        @NotNull @Min(0) @Max(100) Integer cashierDiscountPercent,
        @NotNull @Min(0) @Max(999999999999L) Long managerApprovalLimit) {}
