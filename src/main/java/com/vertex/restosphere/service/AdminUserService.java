package com.vertex.restosphere.service;

import com.vertex.restosphere.common.enums.AccountStatus;
import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.dto.response.AdminUserResponse;
import com.vertex.restosphere.entity.AuditLog;
import com.vertex.restosphere.entity.User;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.repository.AuditLogRepository;
import com.vertex.restosphere.repository.RefreshTokenRepository;
import com.vertex.restosphere.repository.UserRepository;
import com.vertex.restosphere.security.CurrentUserService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminUserService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final AuditLogRepository auditLogs;
    private final CurrentUserService currentUser;
    private final RestaurantLock restaurantLock;

    @Transactional(readOnly = true)
    public List<AdminUserResponse> list(UUID actorId) {
        currentUser.require(actorId, Role.ADMIN);
        return users.findAllByOrderByCreatedAtDesc().stream().map(AdminUserResponse::from).toList();
    }

    public AdminUserResponse updateStatus(UUID actorId, UUID targetId, AccountStatus status) {
        // Khoa truoc roi moi kiem tra actor: neu 2 Admin khoa nhau cung luc, nguoi den sau
        // thay minh da bi khoa -> 403, nen he thong khong bao gio mat het Admin.
        restaurantLock.acquire();
        User actor = currentUser.require(actorId, Role.ADMIN);
        if (actor.getId().equals(targetId)) {
            throw ApiException.conflict("Cannot change your own access");
        }
        User target = users.findById(targetId).orElseThrow(() -> ApiException.notFound("User"));
        boolean enable = status == AccountStatus.ACTIVE;
        if (target.isEnabled() == enable) {
            return AdminUserResponse.from(target);
        }
        target.setEnabled(enable);
        if (!enable && target.getRole() == Role.ADMIN && users.countByRoleAndEnabledTrue(Role.ADMIN) == 0) {
            // Exception lam rollback ca transaction nen setEnabled o tren khong duoc luu.
            throw ApiException.conflict("Cannot remove last active administrator");
        }
        if (!enable) {
            // Access token con toi da 15 phut; thu hoi refresh token de khong gia han them duoc.
            refreshTokens.revokeAllByUser(target.getId(), Instant.now());
        }

        AuditLog log = new AuditLog();
        log.setActor(actor);
        log.setAction(enable ? "ACCOUNT_UNLOCKED" : "ACCOUNT_LOCKED");
        log.setTargetType("USER");
        log.setTargetId(target.getId());
        log.setDescription(target.getEmail());
        auditLogs.save(log);
        return AdminUserResponse.from(target);
    }
}
