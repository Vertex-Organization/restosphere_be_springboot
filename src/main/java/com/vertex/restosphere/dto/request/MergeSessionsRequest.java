package com.vertex.restosphere.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Ghép phiên {@code sourceSessionId} vào phiên của URL: hai bàn dùng chung một hóa đơn. */
public record MergeSessionsRequest(@NotNull UUID sourceSessionId) {}
