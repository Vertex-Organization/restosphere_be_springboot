package com.vertex.restosphere.service;

import com.vertex.restosphere.dto.request.CashierCloseShiftRequest;
import com.vertex.restosphere.dto.request.CashierOpenShiftRequest;
import com.vertex.restosphere.dto.response.CashierScheduledShiftResponse;
import com.vertex.restosphere.dto.response.CashierShiftResponse;
import java.util.List;
import java.util.UUID;

public interface CashierShiftService {

    List<CashierShiftResponse> list(UUID actorId);

    List<CashierScheduledShiftResponse> scheduled(UUID actorId);

    CashierShiftResponse open(UUID actorId, CashierOpenShiftRequest req);

    CashierShiftResponse close(UUID actorId, UUID shiftId, CashierCloseShiftRequest req);
}
