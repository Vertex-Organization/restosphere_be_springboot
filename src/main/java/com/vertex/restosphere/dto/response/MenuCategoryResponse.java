package com.vertex.restosphere.dto.response;

import com.vertex.restosphere.common.enums.MenuGroup;
import com.vertex.restosphere.entity.MenuCategory;
import java.util.UUID;

public record MenuCategoryResponse(
        UUID id, String name, int sortOrder, boolean visible, MenuGroup group) {

    public static MenuCategoryResponse from(MenuCategory c) {
        return new MenuCategoryResponse(
                c.getId(), c.getName(), c.getSortOrder(), c.isVisible(), c.getMenuGroup());
    }
}
