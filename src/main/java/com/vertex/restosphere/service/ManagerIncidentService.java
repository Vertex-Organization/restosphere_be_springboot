package com.vertex.restosphere.service;

import com.vertex.restosphere.dto.request.ManagerIncidentUpdateRequest;
import com.vertex.restosphere.dto.response.ManagerIncidentResponse;
import java.util.List;
import java.util.UUID;

public interface ManagerIncidentService {

    List<ManagerIncidentResponse> list(UUID actorId);

    ManagerIncidentResponse update(UUID actorId, UUID id, ManagerIncidentUpdateRequest req);
}
