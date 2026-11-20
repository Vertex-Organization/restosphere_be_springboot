package com.vertex.restosphere.service;

import com.vertex.restosphere.dto.response.ManagerOrderResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ManagerOrderService {

    List<ManagerOrderResponse> list(UUID actorId, LocalDate date);
}
