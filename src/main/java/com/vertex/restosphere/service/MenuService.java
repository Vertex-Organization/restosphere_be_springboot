package com.vertex.restosphere.service;

import com.vertex.restosphere.common.enums.MenuItemStatus;
import com.vertex.restosphere.dto.request.MenuCategoryRequest;
import com.vertex.restosphere.dto.request.MenuItemRequest;
import com.vertex.restosphere.dto.response.MenuCategoryResponse;
import com.vertex.restosphere.dto.response.MenuItemResponse;
import java.util.List;
import java.util.UUID;

public interface MenuService {

    List<MenuCategoryResponse> listCategories(boolean includeHidden);

    MenuCategoryResponse createCategory(UUID actorId, MenuCategoryRequest req);

    MenuCategoryResponse updateCategory(UUID actorId, UUID id, MenuCategoryRequest req);

    void deleteCategory(UUID actorId, UUID id);

    List<MenuItemResponse> listItems(UUID categoryId, boolean includeHidden);

    MenuItemResponse createItem(UUID actorId, MenuItemRequest req);

    MenuItemResponse updateItem(UUID actorId, UUID id, MenuItemRequest req);

    MenuItemResponse updateItemStatus(UUID id, MenuItemStatus status);

    void deleteItem(UUID actorId, UUID id);
}
