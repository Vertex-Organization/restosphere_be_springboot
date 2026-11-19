package com.vertex.restosphere.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Ngay khong ton tai (30/02), end <= start, ca > 12 gio, id trung: kiem tra o service. */
public record ShiftRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String date,
        @NotNull @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d") String start,
        @NotNull @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d") String end,
        @NotNull @Min(1) @Max(30) Integer minimum,
        @NotEmpty @Size(max = 50) List<@NotNull UUID> employeeIds) {
}
