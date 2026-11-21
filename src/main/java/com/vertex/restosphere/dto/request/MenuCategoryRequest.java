package com.vertex.restosphere.dto.request;

import com.vertex.restosphere.common.enums.MenuGroup;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** group null: tao moi thi "food", sua thi giu nhom cu. */
public record MenuCategoryRequest(
        @NotBlank @Size(max = 100) String name,
        @Min(0) @Max(999) int sortOrder,
        @NotNull Boolean visible,
        MenuGroup group) {}
