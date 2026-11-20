package com.vertex.restosphere.service;

import com.vertex.restosphere.dto.response.ManagerActivityResponse;
import com.vertex.restosphere.dto.response.ManagerPaymentResponse;
import com.vertex.restosphere.dto.response.ManagerSessionResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ManagerOverviewService {

    List<ManagerSessionResponse> sessions(UUID actorId);

    List<ManagerPaymentResponse> payments(UUID actorId, LocalDate date);

    List<ManagerActivityResponse> activity(UUID actorId, UUID entityId, int limit);
}
