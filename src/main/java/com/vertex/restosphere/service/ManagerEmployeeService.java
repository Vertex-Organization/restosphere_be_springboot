package com.vertex.restosphere.service;

import com.vertex.restosphere.dto.request.AssignEmployeeAreaRequest;
import com.vertex.restosphere.dto.response.ManagerEmployeeResponse;
import java.util.List;
import java.util.UUID;

public interface ManagerEmployeeService {

    List<ManagerEmployeeResponse> list(UUID actorId);

    ManagerEmployeeResponse assignArea(UUID actorId, UUID id, AssignEmployeeAreaRequest req);
}
