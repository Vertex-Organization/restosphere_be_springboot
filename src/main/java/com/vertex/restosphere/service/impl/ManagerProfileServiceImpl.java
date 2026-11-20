package com.vertex.restosphere.service.impl;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.dto.response.ManagerProfileResponse;
import com.vertex.restosphere.entity.RestaurantSettings;
import com.vertex.restosphere.entity.User;
import com.vertex.restosphere.repository.RestaurantSettingsRepository;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.ManagerProfileService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagerProfileServiceImpl implements ManagerProfileService {

    private static final List<String> PERMISSIONS = List.of("tables.edit", "reservations.edit", "employees.assign",
            "shifts.manage", "menu.manage", "approvals.decide", "incidents.manage", "reports.export");

    private final CurrentUserService currentUser;
    private final RestaurantSettingsRepository settings;

    @Transactional(readOnly = true)
    @Override
    public ManagerProfileResponse me(UUID actorId) {
        User actor = currentUser.require(actorId, Role.ADMIN, Role.MANAGER);
        long approvalLimit = settings.findBySingletonKey(1).orElseGet(RestaurantSettings::new).getManagerApprovalLimit();
        return ManagerProfileResponse.from(actor, PERMISSIONS, approvalLimit);
    }
}
