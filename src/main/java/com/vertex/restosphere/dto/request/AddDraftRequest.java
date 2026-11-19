package com.vertex.restosphere.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Thêm một món vào giỏ của phục vụ (mặc định số lượng 1). */
public record AddDraftRequest(@NotNull UUID menuItemId) {
}
