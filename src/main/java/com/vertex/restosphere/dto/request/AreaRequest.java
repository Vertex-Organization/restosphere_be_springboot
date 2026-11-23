package com.vertex.restosphere.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code active} và {@code sortOrder} để trống nghĩa là giữ nguyên (tạo mới: true và 0). */
public record AreaRequest(
        @NotBlank @Size(max = 100) String name, Boolean active, @Min(0) Integer sortOrder) {}
