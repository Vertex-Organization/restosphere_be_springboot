package com.vertex.restosphere.service;

import com.vertex.restosphere.dto.response.ManagerProfileResponse;
import java.util.UUID;

public interface ManagerProfileService {

    ManagerProfileResponse me(UUID actorId);
}
