package com.vertex.restosphere.dto.response;

import com.vertex.restosphere.entity.Reservation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Một dòng "Cọc chờ thu": đặt bàn lễ tân đã chuyển sang thu ngân thu cọc tiền mặt. */
public record CashierPendingDepositResponse(
        UUID reservationId,
        String code,
        String customerName,
        String phone,
        Instant startsAt,
        BigDecimal amount,
        Instant dueAt,
        Instant requestedAt) {
    public static CashierPendingDepositResponse from(Reservation r) {
        return new CashierPendingDepositResponse(
                r.getId(),
                r.getCode(),
                r.getCustomerName(),
                r.getPhone(),
                r.getStartsAt(),
                r.getDepositAmount(),
                r.getDepositDueAt(),
                r.getDepositCashRequestedAt());
    }
}
