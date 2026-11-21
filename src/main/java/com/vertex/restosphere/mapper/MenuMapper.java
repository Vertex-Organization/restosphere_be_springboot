package com.vertex.restosphere.mapper;

import static com.vertex.restosphere.common.util.Values.id;
import static com.vertex.restosphere.common.util.Values.map;
import static com.vertex.restosphere.common.util.Values.str;

import com.vertex.restosphere.entity.ApprovalRequest;
import com.vertex.restosphere.entity.MenuCategory;
import com.vertex.restosphere.entity.MenuItem;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Thực đơn và yêu cầu đổi trạng thái món dạng JSON cho các workspace. */
@Component
public class MenuMapper {

    public Map<String, Object> toMap(MenuCategory c) {
        return map(
                "id",
                id(c),
                "name",
                c.getName(),
                "visible",
                c.isVisible(),
                "sort",
                c.getSortOrder(),
                "sortOrder",
                c.getSortOrder(),
                "menuGroup",
                c.getMenuGroup());
    }

    public Map<String, Object> toMap(MenuItem i) {
        return map(
                "id",
                id(i),
                "name",
                i.getName(),
                "categoryId",
                id(i.getCategory()),
                "price",
                i.getPrice(),
                "priceUnit",
                i.getPriceUnit(),
                "unit",
                i.getPriceUnit(),
                "image",
                str(i.getImageUrl()),
                "imageUrl",
                i.getImageUrl(),
                "description",
                str(i.getDescription()),
                "status",
                i.getStatus(),
                "updatedAt",
                i.getUpdatedAt());
    }

    public Map<String, Object> toRequestMap(ApprovalRequest r) {
        return map(
                "id",
                id(r),
                "menuItemId",
                r.getTargetId(),
                "requested",
                r.getMenuStatus(),
                "requestedBy",
                id(r.getRequestedBy()),
                "requestedAt",
                r.getCreatedAt(),
                "status",
                r.getStatus(),
                "decidedAt",
                r.getDecidedAt(),
                "decidedBy",
                id(r.getDecidedBy()));
    }
}
