package com.vertex.restosphere.dto.response;

import com.vertex.restosphere.entity.Area;
import java.util.UUID;

public record AreaResponse(UUID id, String name, boolean active, int sortOrder) {

    public static AreaResponse from(Area a) {
        return new AreaResponse(a.getId(), a.getName(), a.isActive(), a.getSortOrder());
    }
}
