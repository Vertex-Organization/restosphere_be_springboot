package com.vertex.restosphere.service.impl;

import com.vertex.restosphere.common.enums.AdminAuditAction;
import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.dto.response.AuditLogResponse;
import com.vertex.restosphere.entity.AuditLog;
import com.vertex.restosphere.entity.RestaurantSettings;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.repository.AuditLogRepository;
import com.vertex.restosphere.repository.RestaurantSettingsRepository;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.AdminAuditLogService;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAuditLogServiceImpl implements AdminAuditLogService {

    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT = 500;
    private static final List<String> ADMIN_ACTIONS =
            Arrays.stream(AdminAuditAction.values()).map(Enum::name).toList();

    private final CurrentUserService currentUser;
    private final AuditLogRepository auditLogs;
    private final RestaurantSettingsRepository settings;

    @Override
    public List<AuditLogResponse> list(
            UUID actorId,
            String action,
            String actor,
            LocalDate from,
            LocalDate to,
            Integer limit) {
        currentUser.require(actorId, Role.ADMIN);

        if (action != null && !ADMIN_ACTIONS.contains(action)) {
            throw invalid("action must be one of " + String.join(", ", ADMIN_ACTIONS));
        }
        String name = actor == null || actor.isBlank() ? null : actor.strip();
        if (name != null && name.length() > 100) {
            throw invalid("actor must be at most 100 characters");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw invalid("from must not be after to");
        }
        int size = limit == null ? DEFAULT_LIMIT : limit;
        if (size < 1 || size > MAX_LIMIT) {
            throw invalid("limit must be between 1 and " + MAX_LIMIT);
        }

        ZoneId zone =
                settings.findBySingletonKey(1)
                        .map(RestaurantSettings::getTimezone)
                        .map(ZoneId::of)
                        .orElse(ZoneId.of("Asia/Ho_Chi_Minh"));

        Specification<AuditLog> spec =
                (root, query, cb) -> {
                    List<Predicate> where = new ArrayList<>();
                    where.add(
                            root.get("action")
                                    .in(action == null ? ADMIN_ACTIONS : List.of(action)));
                    if (name != null) {
                        where.add(
                                cb.like(
                                        cb.lower(root.join("actor", JoinType.LEFT).get("fullName")),
                                        "%" + escapeLike(name.toLowerCase(Locale.ROOT)) + "%",
                                        '\\'));
                    }
                    if (from != null) {
                        where.add(
                                cb.greaterThanOrEqualTo(
                                        root.get("createdAt"),
                                        from.atStartOfDay(zone).toInstant()));
                    }
                    if (to != null) {
                        where.add(
                                cb.lessThan(
                                        root.get("createdAt"),
                                        to.plusDays(1).atStartOfDay(zone).toInstant()));
                    }
                    return cb.and(where.toArray(Predicate[]::new));
                };
        return auditLogs
                .findAll(spec, PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(AuditLogResponse::from)
                .getContent();
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
