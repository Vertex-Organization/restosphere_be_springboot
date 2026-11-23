package com.vertex.restosphere.dto.response;

import java.util.UUID;

/** Kết quả ghép/tách: phiên và hóa đơn sau khi đổi. */
public record SessionChangeResponse(UUID sessionId, UUID billId, String billCode) {}
