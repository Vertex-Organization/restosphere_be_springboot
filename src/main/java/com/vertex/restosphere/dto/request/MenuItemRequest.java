package com.vertex.restosphere.dto.request;

import com.vertex.restosphere.common.enums.MenuItemStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/** status/unit null: tao moi thi AVAILABLE/portion, sua thi giu gia tri cu. */
public record MenuItemRequest(
        @NotBlank @Size(max = 150) String name,
        @NotNull UUID categoryId,
        @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 0) BigDecimal price,
        @Size(max = 1000) String description,
        // Rong, duong dan noi bo "/..." (khong phai "//") hoac https://.
        @Size(max = 500) @Pattern(regexp = "\\s*|\\s*(/(?!/)|https://).*") String imageUrl,
        MenuItemStatus status,
        @Pattern(regexp = "portion|kg") String unit) {
}
