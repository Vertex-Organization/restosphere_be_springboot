package com.vertex.restosphere.service;

import com.vertex.restosphere.dto.request.KitchenItemRef;
import com.vertex.restosphere.dto.response.KitchenOrderItemResponse;
import com.vertex.restosphere.dto.response.KitchenOrderResponse;
import com.vertex.restosphere.entity.OrderItem;
import java.util.List;
import java.util.UUID;

public interface KitchenService {

    List<KitchenOrderResponse> listOrders(UUID actorId, OrderItem.Status status);

    List<KitchenOrderItemResponse> start(UUID actorId, List<KitchenItemRef> refs);

    List<KitchenOrderItemResponse> complete(UUID actorId, List<KitchenItemRef> refs);

    List<KitchenOrderItemResponse> reject(UUID actorId, UUID itemId, long version, String reason);
}
