package com.vertex.restosphere.service;

import com.vertex.restosphere.common.enums.AccountStatus;
import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.dto.request.CreateEmployeeRequest;
import com.vertex.restosphere.dto.response.AdminEmployeeResponse;
import java.util.List;
import java.util.UUID;

public interface AdminEmployeeService {

    List<AdminEmployeeResponse> list(UUID actorId);

    AdminEmployeeResponse updateStatus(UUID actorId, UUID targetId, AccountStatus status);

    AdminEmployeeResponse updateRole(UUID actorId, UUID targetId, Role role);

    AdminEmployeeResponse create(UUID actorId, CreateEmployeeRequest req);
}
