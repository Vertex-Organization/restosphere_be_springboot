package com.vertex.restosphere.service;

import com.vertex.restosphere.dto.response.CashierMeResponse;
import com.vertex.restosphere.dto.response.CashierNotificationResponse;
import java.util.List;
import java.util.UUID;

public interface CashierNotificationService {

    List<CashierNotificationResponse> list(UUID actorId);

    List<CashierNotificationResponse> markRead(UUID actorId, List<String> ids);

    CashierMeResponse me(UUID actorId);
}
