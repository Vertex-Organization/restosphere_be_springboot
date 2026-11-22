package com.vertex.restosphere.dto.response;

import com.vertex.restosphere.common.enums.AccountStatus;
import com.vertex.restosphere.entity.Reservation;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** {@code recentReservations} chỉ có ở API chi tiết; danh sách trả về mảng rỗng. */
public record CustomerResponse(
        UUID id,
        String fullName,
        String phone,
        String email,
        LocalDate birthday,
        boolean marketingConsent,
        String note,
        AccountStatus status,
        Instant createdAt,
        long reservationCount,
        Instant lastVisitAt,
        List<RecentReservation> recentReservations) {

    public record RecentReservation(
            UUID id,
            String code,
            Instant startsAt,
            int guests,
            String areaName,
            Reservation.Status status) {}
}
