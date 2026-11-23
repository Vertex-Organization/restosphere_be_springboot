package com.vertex.restosphere.dto.response;

import com.vertex.restosphere.common.enums.Role;
import java.util.UUID;

public record EmployeeResponse(UUID id, String email, String fullName, Role role) {}
