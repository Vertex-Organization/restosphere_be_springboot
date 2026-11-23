package com.vertex.restosphere.service.impl;

import com.vertex.restosphere.common.enums.AdminAuditAction;
import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.dto.request.DiscountLimitRequest;
import com.vertex.restosphere.dto.response.DiscountLimitResponse;
import com.vertex.restosphere.entity.AuditLog;
import com.vertex.restosphere.entity.Employee;
import com.vertex.restosphere.entity.RestaurantSettings;
import com.vertex.restosphere.repository.AuditLogRepository;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.DiscountLimitService;
import com.vertex.restosphere.service.WorkspaceStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class DiscountLimitServiceImpl implements DiscountLimitService {

    private final WorkspaceStore store;
    private final CurrentUserService currentUser;
    private final AuditLogRepository auditLogs;

    @Override
    public DiscountLimitResponse get(UUID actorId) {
        currentUser.require(actorId, Role.ADMIN);
        return DiscountLimitResponse.from(store.lock());
    }

    @Override
    public DiscountLimitResponse update(UUID actorId, DiscountLimitRequest req) {
        RestaurantSettings s = store.lock();
        Employee actor = currentUser.require(actorId, Role.ADMIN);

        List<String> changed = new ArrayList<>();
        if (s.getCashierDiscountPercent() != req.cashierDiscountPercent()) {
            s.setCashierDiscountPercent(req.cashierDiscountPercent());
            changed.add("cashierDiscountPercent");
        }
        if (s.getManagerApprovalLimit() != req.managerApprovalLimit()) {
            s.setManagerApprovalLimit(req.managerApprovalLimit());
            changed.add("managerApprovalLimit");
        }
        if (changed.isEmpty()) {
            return DiscountLimitResponse.from(s);
        }

        AuditLog log = new AuditLog();
        log.setActor(actor);
        log.setAction(AdminAuditAction.SETTINGS_UPDATED.name());
        log.setTargetType("SETTINGS");
        log.setTargetId(s.getId());
        log.setDescription("Settings updated: " + String.join(", ", changed));
        auditLogs.save(log);
        return DiscountLimitResponse.from(s);
    }
}
