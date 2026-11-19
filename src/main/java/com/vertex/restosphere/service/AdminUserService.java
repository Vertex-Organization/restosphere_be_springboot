package com.vertex.restosphere.service;

import com.vertex.restosphere.common.enums.AccountStatus;
import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.dto.response.AdminUserResponse;
import java.util.List;
import java.util.UUID;

public interface AdminUserService {

    List<AdminUserResponse> list(UUID actorId);

    AdminUserResponse updateStatus(UUID actorId, UUID targetId, AccountStatus status);

    AdminUserResponse updateRole(UUID actorId, UUID targetId, Role role);
}
