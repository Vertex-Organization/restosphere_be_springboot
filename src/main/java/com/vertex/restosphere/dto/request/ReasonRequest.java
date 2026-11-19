package com.vertex.restosphere.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Lý do kèm theo yêu cầu (hủy món, ưu tiên lượt gọi...). */
public record ReasonRequest(@NotBlank @Size(max = 1000) String reason) {
}
