package com.vertex.restosphere.service;

import com.vertex.restosphere.dto.request.ShiftRequest;
import com.vertex.restosphere.dto.response.ShiftResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ManagerShiftService {

    List<ShiftResponse> list(UUID actorId, LocalDate from, LocalDate to);

    ShiftResponse create(UUID actorId, ShiftRequest req);

    ShiftResponse update(UUID actorId, UUID id, ShiftRequest req);
}
