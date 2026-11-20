package com.vertex.restosphere.service.impl;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.dto.response.ManagerActivityResponse;
import com.vertex.restosphere.dto.response.ManagerPaymentResponse;
import com.vertex.restosphere.dto.response.ManagerSessionResponse;
import com.vertex.restosphere.entity.RestaurantSettings;
import com.vertex.restosphere.entity.TableSession;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.repository.ManagerQueryRepository;
import com.vertex.restosphere.repository.RestaurantSettingsRepository;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.ManagerOverviewService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagerOverviewServiceImpl implements ManagerOverviewService {

    private final CurrentUserService currentUser;
    private final ManagerQueryRepository queries;
    private final RestaurantSettingsRepository settings;

    @Transactional(readOnly = true)
    @Override
    public List<ManagerSessionResponse> sessions(UUID actorId) {
        currentUser.require(actorId, Role.ADMIN, Role.MANAGER);
        return queries.findOpenSessions(TableSession.Status.CLOSED).stream().map(ManagerSessionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    @Override
    public List<ManagerPaymentResponse> payments(UUID actorId, LocalDate date) {
        currentUser.require(actorId, Role.ADMIN, Role.MANAGER);
        ZoneId zone = settings.findBySingletonKey(1).map(RestaurantSettings::getTimezone).map(ZoneId::of)
                .orElse(ZoneId.of("Asia/Ho_Chi_Minh"));
        LocalDate day = date == null ? LocalDate.now(zone) : date;
        return queries.findPaymentsBetween(day.atStartOfDay(zone).toInstant(),
                day.plusDays(1).atStartOfDay(zone).toInstant()).stream().map(ManagerPaymentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    @Override
    public List<ManagerActivityResponse> activity(UUID actorId, UUID entityId, int limit) {
        currentUser.require(actorId, Role.ADMIN, Role.MANAGER);
        if (limit < 1 || limit > 200) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 200");
        }
        PageRequest page = PageRequest.of(0, limit);
        return (entityId == null ? queries.findRecentActivity(page) : queries.findActivityByTarget(entityId, page))
                .stream().map(ManagerActivityResponse::from).toList();
    }
}
