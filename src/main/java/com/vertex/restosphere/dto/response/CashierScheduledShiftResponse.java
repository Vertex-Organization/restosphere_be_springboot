package com.vertex.restosphere.dto.response;

import com.vertex.restosphere.entity.ShiftAssignment;
import java.time.Instant;
import java.util.UUID;

/** Matches the FE ScheduledShift type: one row per cashier assignment. */
public record CashierScheduledShiftResponse(UUID id, String name, Instant start, Instant end, UUID cashierId) {

    public static CashierScheduledShiftResponse from(ShiftAssignment a) {
        return new CashierScheduledShiftResponse(a.getShift().getId(), a.getShift().getName(),
                a.getShift().getStartsAt(), a.getShift().getEndsAt(), a.getUser().getId());
    }
}
