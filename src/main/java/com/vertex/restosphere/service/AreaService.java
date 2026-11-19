package com.vertex.restosphere.service;

import com.vertex.restosphere.dto.request.AreaRequest;
import com.vertex.restosphere.dto.response.AreaResponse;
import java.util.List;
import java.util.UUID;

public interface AreaService {

    List<AreaResponse> list();

    AreaResponse create(AreaRequest req);

    AreaResponse update(UUID id, AreaRequest req);

    void delete(UUID id);
}
