package com.vertex.restosphere.service;

import com.vertex.restosphere.dto.request.RestaurantImageRequest;
import com.vertex.restosphere.dto.request.RestaurantProfileRequest;
import com.vertex.restosphere.dto.response.RestaurantProfileResponse;
import java.util.UUID;

public interface AdminRestaurantService {

    RestaurantProfileResponse get(UUID actorId);

    RestaurantProfileResponse update(UUID actorId, RestaurantProfileRequest req);

    RestaurantProfileResponse uploadImage(UUID actorId, String kind, RestaurantImageRequest req);

    RestaurantProfileResponse removeImage(UUID actorId, String kind);
}
