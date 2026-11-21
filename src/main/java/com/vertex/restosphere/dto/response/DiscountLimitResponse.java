package com.vertex.restosphere.dto.response;

import com.vertex.restosphere.entity.RestaurantSettings;

public record DiscountLimitResponse(int cashierDiscountPercent, long managerApprovalLimit) {

    public static DiscountLimitResponse from(RestaurantSettings s) {
        return new DiscountLimitResponse(
                s.getCashierDiscountPercent(), s.getManagerApprovalLimit());
    }
}
