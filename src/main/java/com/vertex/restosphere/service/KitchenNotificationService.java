package com.vertex.restosphere.service;

import com.vertex.restosphere.dto.response.KitchenMeResponse;
import com.vertex.restosphere.dto.response.KitchenNotificationResponse;
import java.util.List;
import java.util.UUID;

public interface KitchenNotificationService {

    List<KitchenNotificationResponse> list(UUID actorId);

    List<KitchenNotificationResponse> markRead(UUID actorId, List<String> ids);

    KitchenMeResponse me(UUID actorId);
}
