package com.vertex.restosphere.service.impl;

import com.vertex.restosphere.common.enums.AccountStatus;
import com.vertex.restosphere.common.enums.AdminAuditAction;
import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.common.util.EmailUtils;
import com.vertex.restosphere.common.util.TemporaryPasswordGenerator;
import com.vertex.restosphere.dto.request.CreateEmployeeRequest;
import com.vertex.restosphere.dto.response.AdminEmployeeResponse;
import com.vertex.restosphere.entity.AuditLog;
import com.vertex.restosphere.entity.Employee;
import com.vertex.restosphere.entity.TableSession;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.repository.AuditLogRepository;
import com.vertex.restosphere.repository.CashierShiftRepository;
import com.vertex.restosphere.repository.EmployeeRepository;
import com.vertex.restosphere.repository.RefreshTokenRepository;
import com.vertex.restosphere.repository.TableSessionRepository;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.AccountMailService;
import com.vertex.restosphere.service.AdminEmployeeService;
import com.vertex.restosphere.service.RestaurantLock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminEmployeeServiceImpl implements AdminEmployeeService {

    private final EmployeeRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final AuditLogRepository auditLogs;
    private final TableSessionRepository tableSessions;
    private final CashierShiftRepository cashierShifts;
    private final CurrentUserService currentUser;
    private final RestaurantLock restaurantLock;
    private final PasswordEncoder passwordEncoder;
    private final AccountMailService accountMailService;

    @Transactional(readOnly = true)
    @Override
    public List<AdminEmployeeResponse> list(UUID actorId) {
        currentUser.require(actorId, Role.ADMIN);
        return users.findAllByOrderByCreatedAtDesc().stream()
                .map(AdminEmployeeResponse::from)
                .toList();
    }

    @Override
    public AdminEmployeeResponse updateStatus(UUID actorId, UUID targetId, AccountStatus status) {
        restaurantLock.acquire();
        Employee actor = currentUser.require(actorId, Role.ADMIN);
        if (actor.getId().equals(targetId)) {
            throw ApiException.conflict("Cannot change your own access");
        }
        Employee target =
                users.findById(targetId).orElseThrow(() -> ApiException.notFound("Employee"));
        boolean enable = status == AccountStatus.ACTIVE;
        if (target.isEnabled() == enable) {
            return AdminEmployeeResponse.from(target);
        }
        target.setEnabled(enable);
        if (!enable
                && target.getRole() == Role.ADMIN
                && users.countByRoleAndEnabledTrue(Role.ADMIN) == 0) {
            throw ApiException.conflict("Cannot remove last active administrator");
        }
        if (!enable) {
            refreshTokens.revokeAllByUser(target.getId(), Instant.now());
        }

        AuditLog log = new AuditLog();
        log.setActor(actor);
        log.setAction(
                (enable ? AdminAuditAction.ACCOUNT_UNLOCKED : AdminAuditAction.ACCOUNT_LOCKED)
                        .name());
        log.setTargetType("USER");
        log.setTargetId(target.getId());
        log.setDescription(target.getEmail());
        auditLogs.save(log);
        return AdminEmployeeResponse.from(target);
    }

    @Override
    public AdminEmployeeResponse updateRole(UUID actorId, UUID targetId, Role role) {
        restaurantLock.acquire();
        Employee actor = currentUser.require(actorId, Role.ADMIN);
        if (actor.getId().equals(targetId)) {
            throw ApiException.conflict("Cannot change your own access");
        }
        Employee target =
                users.findById(targetId).orElseThrow(() -> ApiException.notFound("Employee"));
        if (target.getRole() == role) {
            return AdminEmployeeResponse.from(target);
        }
        if (target.getRole() == Role.ADMIN
                && target.isEnabled()
                && users.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
            throw ApiException.conflict("Cannot remove last active administrator");
        }
        if (tableSessions.existsByWaiterIdAndStatusNot(targetId, TableSession.Status.CLOSED)
                || cashierShifts.existsByCashierIdAndClosedAtIsNull(targetId)) {
            throw ApiException.conflict("Account has active work");
        }
        Role oldRole = target.getRole();
        target.setRole(role);

        AuditLog log = new AuditLog();
        log.setActor(actor);
        log.setAction(AdminAuditAction.ROLE_CHANGED.name());
        log.setTargetType("USER");
        log.setTargetId(target.getId());
        log.setDescription(target.getEmail() + ": " + oldRole + " → " + role);
        auditLogs.save(log);
        return AdminEmployeeResponse.from(target);
    }

    @Override
    public AdminEmployeeResponse create(UUID actorId, CreateEmployeeRequest req) {
        restaurantLock.acquire();
        Employee actor = currentUser.require(actorId, Role.ADMIN);
        if (!accountMailService.isEnabled()) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Email service is not configured");
        }
        String email = EmailUtils.normalize(req.email());
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("Email already exists");
        }
        String password = TemporaryPasswordGenerator.generate();
        Employee user = new Employee();
        user.setEmail(email);
        user.setFullName(req.fullName().strip());
        user.setRole(req.role());
        user.setEnabled(true);
        user.setPasswordHash(passwordEncoder.encode(password));
        users.saveAndFlush(user);

        AuditLog log = new AuditLog();
        log.setActor(actor);
        log.setAction(AdminAuditAction.USER_CREATED.name());
        log.setTargetType("USER");
        log.setTargetId(user.getId());
        log.setDescription(email + ": " + req.role());
        auditLogs.save(log);

        try {
            accountMailService.sendTemporaryPassword(user, password);
        } catch (MailException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Could not send email");
        }
        return AdminEmployeeResponse.from(user);
    }
}
