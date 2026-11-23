package com.vertex.restosphere.dto.response;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.entity.Employee;
import java.util.List;
import java.util.UUID;

public record ManagerProfileResponse(
        UUID id,
        String name,
        String email,
        Role role,
        String position,
        List<String> permissions,
        long approvalLimit) {

    public static ManagerProfileResponse from(
            Employee u, List<String> permissions, long approvalLimit) {
        return new ManagerProfileResponse(
                u.getId(),
                u.getFullName(),
                u.getEmail(),
                u.getRole(),
                u.getRole().name(),
                permissions,
                approvalLimit);
    }
}
