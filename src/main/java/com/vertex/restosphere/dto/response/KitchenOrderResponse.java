package com.vertex.restosphere.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Mot luot goi mon (phieu bep), khop KitchenOrder cua FE kem danh sach mon. */
public record KitchenOrderResponse(
        UUID id,
        String code,
        UUID sessionId,
        String tableCode,
        String area,
        int guests,
        UUID waiterId,
        int sequence,
        Instant submittedAt,
        Priority priority,
        List<KitchenOrderItemResponse> items) {

    public record Priority(Instant at, String by, String reason) {}
}
