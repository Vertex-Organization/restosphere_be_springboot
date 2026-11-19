package com.vertex.restosphere.dto.response;

import com.vertex.restosphere.common.enums.Role;
import java.util.List;
import java.util.UUID;

public record KitchenMeResponse(UUID id, String name, String username, Role role, String position, String shift,
        List<String> permissions) {}
