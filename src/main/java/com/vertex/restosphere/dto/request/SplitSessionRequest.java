package com.vertex.restosphere.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/** Tách các món được chọn sang một bàn trống khác; phiên mới có hóa đơn riêng. */
public record SplitSessionRequest(
        @NotNull UUID targetTableId,
        @NotEmpty List<@NotNull UUID> itemIds,
        @Min(1) int guestCount) {}
