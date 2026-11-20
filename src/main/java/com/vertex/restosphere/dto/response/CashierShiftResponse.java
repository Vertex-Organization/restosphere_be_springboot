package com.vertex.restosphere.dto.response;

import com.vertex.restosphere.entity.CashierShift;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Matches the FE CashierShift type. */
public record CashierShiftResponse(UUID id, String code, UUID cashierId, String status, Instant openedAt,
        BigDecimal openingFloat, UUID scheduledShiftId, Instant closedAt, BigDecimal expectedCash,
        BigDecimal countedCash, String closingNote) {

    public static CashierShiftResponse from(CashierShift s) {
        return new CashierShiftResponse(s.getId(), "S-" + s.getId(), s.getCashier().getId(),
                s.getClosedAt() == null ? "OPEN" : "CLOSED", s.getOpenedAt(), s.getOpeningCash(),
                s.getScheduledShift() == null ? null : s.getScheduledShift().getId(), s.getClosedAt(),
                s.getExpectedCash(), s.getCountedCash(), s.getNote());
    }
}
