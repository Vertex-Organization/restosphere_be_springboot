package com.vertex.restosphere.dto.response;

import com.vertex.restosphere.entity.Reservation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ManagerReservationResponse(
        UUID id,
        String code,
        String customer,
        String phone,
        String email,
        String occasion,
        UUID areaId,
        String areaName,
        Reservation.Source source,
        int guests,
        Integer actualGuests,
        Instant at,
        Instant until,
        UUID tableId,
        String tableCode,
        Reservation.Status status,
        String employeeId,
        String note,
        List<Preorder> preorder) {

    public record Preorder(
            UUID menuItemId, String menuItemName, BigDecimal quantity, String note) {}
}
