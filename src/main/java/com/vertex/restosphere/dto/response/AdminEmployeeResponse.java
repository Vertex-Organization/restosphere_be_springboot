package com.vertex.restosphere.dto.response;

import com.vertex.restosphere.common.enums.AccountStatus;
import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.entity.Employee;
import java.time.Instant;
import java.util.UUID;

public record AdminEmployeeResponse(
        UUID id,
        String fullName,
        String email,
        Role role,
        AccountStatus status,
        Instant createdAt,
        String initials) {

    public static AdminEmployeeResponse from(Employee u) {
        return new AdminEmployeeResponse(
                u.getId(),
                u.getFullName(),
                u.getEmail(),
                u.getRole(),
                u.isEnabled() ? AccountStatus.ACTIVE : AccountStatus.LOCKED,
                u.getCreatedAt(),
                u.getFullName().substring(0, 1).toUpperCase());
    }
}
