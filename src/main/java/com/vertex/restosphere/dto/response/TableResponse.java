package com.vertex.restosphere.dto.response;

import com.vertex.restosphere.common.enums.TableStatus;
import com.vertex.restosphere.entity.RestaurantTable;
import java.util.UUID;

public record TableResponse(
        UUID id, String code, UUID areaId, String areaName, int capacity, TableStatus status) {

    public static TableResponse from(RestaurantTable t) {
        return new TableResponse(
                t.getId(),
                t.getCode(),
                t.getArea().getId(),
                t.getArea().getName(),
                t.getCapacity(),
                t.getStatus());
    }
}
