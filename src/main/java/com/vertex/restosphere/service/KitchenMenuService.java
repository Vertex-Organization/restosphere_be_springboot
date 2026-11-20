package com.vertex.restosphere.service;

import com.vertex.restosphere.common.enums.MenuItemStatus;
import com.vertex.restosphere.dto.response.KitchenMenuItemResponse;
import com.vertex.restosphere.dto.response.KitchenMenuRequestResponse;
import java.util.List;
import java.util.UUID;

public interface KitchenMenuService {

    List<KitchenMenuItemResponse> listItems(UUID actorId);

    KitchenMenuItemResponse setStatus(UUID actorId, String menuItemId, MenuItemStatus status);

    List<KitchenMenuRequestResponse> listRequests(UUID actorId);

    KitchenMenuRequestResponse request(UUID actorId, String menuItemId, MenuItemStatus status);
}
