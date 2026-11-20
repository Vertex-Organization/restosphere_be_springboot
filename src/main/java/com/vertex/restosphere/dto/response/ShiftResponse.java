package com.vertex.restosphere.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ShiftResponse(UUID id, String name, LocalDate date, String start, String end, int minimum,
        List<UUID> employeeIds) {
}
