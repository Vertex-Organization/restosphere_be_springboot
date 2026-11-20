package com.vertex.restosphere.service;

import com.vertex.restosphere.dto.response.ManagerNotificationResponse;
import java.util.List;
import java.util.UUID;

public interface ManagerNotificationService {

    List<ManagerNotificationResponse> list(UUID actorId);

    List<ManagerNotificationResponse> markRead(UUID actorId, List<UUID> ids);
}
