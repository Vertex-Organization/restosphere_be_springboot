package com.vertex.restosphere.service;

import com.vertex.restosphere.dto.request.MenuImageRequest;
import com.vertex.restosphere.dto.response.MenuImageResponse;
import java.util.UUID;

public interface MenuImageService {

    MenuImageResponse upload(UUID actorId, MenuImageRequest req);
}
