package com.vertex.restosphere.dto.response;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.entity.Area;
import com.vertex.restosphere.entity.User;
import java.util.UUID;

public record ManagerEmployeeResponse(UUID id, String name, String email, Role role, String department,
        String position, String status, String areaId, String areaName) {

    public static ManagerEmployeeResponse from(User u) {
        Area area = u.getAssignedArea();
        String department = switch (u.getRole()) {
            case WAITER -> "SERVICE";
            case KITCHEN -> "KITCHEN";
            case CASHIER -> "CASHIER";
            case RECEPTIONIST -> "RECEPTION";
            default -> "MANAGEMENT";
        };
        return new ManagerEmployeeResponse(u.getId(), u.getFullName(), u.getEmail(), u.getRole(), department,
                u.getRole().name(), u.isEnabled() ? "ACTIVE" : "INACTIVE",
                area == null ? "" : area.getId().toString(), area == null ? "" : area.getName());
    }
}
