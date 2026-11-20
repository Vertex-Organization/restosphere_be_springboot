package com.vertex.restosphere.service;

import com.vertex.restosphere.dto.request.KitchenIncidentRequest;
import com.vertex.restosphere.dto.response.KitchenIncidentResponse;
import java.util.List;
import java.util.UUID;

public interface KitchenIncidentService {

    List<KitchenIncidentResponse> list(UUID actorId);

    KitchenIncidentResponse report(UUID actorId, KitchenIncidentRequest req);
}
