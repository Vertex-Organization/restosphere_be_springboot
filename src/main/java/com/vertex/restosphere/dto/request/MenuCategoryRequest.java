package com.vertex.restosphere.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MenuCategoryRequest(@NotBlank @Size(max = 100) String name, int sortOrder, boolean visible) {
}
