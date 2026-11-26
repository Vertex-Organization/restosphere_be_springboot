package com.vertex.restosphere.dto.response;

import com.vertex.restosphere.common.util.Values;
import com.vertex.restosphere.entity.Order;
import com.vertex.restosphere.entity.RestaurantTable;
import com.vertex.restosphere.entity.TableSession;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ManagerOrderResponse(
        UUID id,
        String code,
        UUID sessionId,
        UUID tableId,
        String tableCode,
        String areaName,
        int guests,
        UUID waiterId,
        String waiterName,
        int round,
        String status,
        Instant at,
        boolean paid,
        List<ManagerOrderItemResponse> items) {

    public static String code(Order o) {
        return Values.code("O", o);
    }

    public static ManagerOrderResponse from(
            Order o, List<ManagerOrderItemResponse> items, boolean paid) {
        TableSession s = o.getSession();
        RestaurantTable t = s.getTable();
        return new ManagerOrderResponse(
                o.getId(),
                code(o),
                s.getId(),
                t.getId(),
                t.getCode(),
                t.getArea().getName(),
                s.getGuestCount(),
                o.getWaiter().getId(),
                o.getWaiter().getFullName(),
                o.getRoundNumber(),
                "SUBMITTED",
                o.getCreatedAt(),
                paid,
                items);
    }
}
