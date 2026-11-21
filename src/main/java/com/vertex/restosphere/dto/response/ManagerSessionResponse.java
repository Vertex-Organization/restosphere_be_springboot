package com.vertex.restosphere.dto.response;

import com.vertex.restosphere.entity.TableSession;
import com.vertex.restosphere.entity.User;
import java.time.Instant;
import java.util.UUID;

public record ManagerSessionResponse(
        UUID id,
        UUID tableId,
        String tableCode,
        UUID waiterId,
        String waiterName,
        int guests,
        Instant startedAt,
        boolean paymentRequested) {

    public static ManagerSessionResponse from(TableSession s) {
        User w = s.getWaiter();
        return new ManagerSessionResponse(
                s.getId(),
                s.getTable().getId(),
                s.getTable().getCode(),
                w == null ? null : w.getId(),
                w == null ? null : w.getFullName(),
                s.getGuestCount(),
                s.getStartedAt(),
                s.getStatus() == TableSession.Status.PAYMENT_REQUESTED);
    }
}
