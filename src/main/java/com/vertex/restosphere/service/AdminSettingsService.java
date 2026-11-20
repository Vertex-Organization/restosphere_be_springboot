package com.vertex.restosphere.service;

import com.vertex.restosphere.dto.request.SystemSettingsRequest;
import com.vertex.restosphere.dto.response.SystemSettingsResponse;
import java.util.UUID;

public interface AdminSettingsService {

    SystemSettingsResponse get(UUID actorId);

    SystemSettingsResponse update(UUID actorId, SystemSettingsRequest req);
}
