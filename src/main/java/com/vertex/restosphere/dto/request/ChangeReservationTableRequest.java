package com.vertex.restosphere.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** reason toi thieu 5 ky tu sau khi cat khoang trang: kiem tra o service. */
public record ChangeReservationTableRequest(@NotNull UUID tableId, @NotBlank @Size(max = 1000) String reason) {
}
