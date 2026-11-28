package com.vertex.restosphere.dto.request;

import com.vertex.restosphere.exception.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/** Tách các món được chọn sang một bàn trống khác; phiên mới có hóa đơn riêng. */
public record SplitSessionRequest(
        @NotNull UUID targetTableId,
        List<@NotNull UUID> itemIds,
        List<@NotNull @Valid SplitItemRequest> items,
        @Min(1) int guestCount) {

    public List<SplitItemRequest> lines() {
        if (items != null && !items.isEmpty()) {
            return items;
        }
        if (itemIds != null && !itemIds.isEmpty()) {
            return itemIds.stream().map(id -> new SplitItemRequest(id, null)).toList();
        }
        throw ApiException.badRequest("items", "Hãy chọn ít nhất một món để tách.");
    }
}
