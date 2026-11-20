package com.vertex.restosphere.service;

import com.vertex.restosphere.dto.request.DiscountLimitRequest;
import com.vertex.restosphere.dto.response.DiscountLimitResponse;
import java.util.UUID;

public interface DiscountLimitService {

    DiscountLimitResponse get(UUID actorId);

    DiscountLimitResponse update(UUID actorId, DiscountLimitRequest req);
}
