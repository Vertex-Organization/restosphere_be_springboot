package com.vertex.restosphere.dto.response;

import com.vertex.restosphere.common.enums.TableStatus;
import java.util.UUID;

public record ManagerTableResponse(UUID id, String code, UUID areaId, String areaName, int seats, TableStatus status,
        UUID sessionId, boolean hasActiveReservation) {
}
