package com.vertex.restosphere.service.impl;

import com.vertex.restosphere.common.enums.AdminAuditAction;
import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.dto.request.SystemSettingsRequest;
import com.vertex.restosphere.dto.response.SystemSettingsResponse;
import com.vertex.restosphere.entity.AuditLog;
import com.vertex.restosphere.entity.Bill;
import com.vertex.restosphere.entity.Employee;
import com.vertex.restosphere.entity.RestaurantSettings;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.repository.AuditLogRepository;
import com.vertex.restosphere.repository.BillRepository;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.AdminSettingsService;
import com.vertex.restosphere.service.WorkspaceStore;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminSettingsServiceImpl implements AdminSettingsService {

    private record Field(
            String name,
            Function<SystemSettingsRequest, Object> in,
            Function<RestaurantSettings, Object> get,
            BiConsumer<RestaurantSettings, Object> set) {}

    private static final List<Field> FIELDS =
            List.of(
                    new Field(
                            "onlineReservations",
                            SystemSettingsRequest::onlineReservations,
                            RestaurantSettings::isOnlineReservations,
                            (s, v) -> s.setOnlineReservations((Boolean) v)),
                    new Field(
                            "tableHoldMinutes",
                            SystemSettingsRequest::tableHoldMinutes,
                            RestaurantSettings::getHoldMinutes,
                            (s, v) -> s.setHoldMinutes((Integer) v)),
                    new Field(
                            "noShowMinutes",
                            SystemSettingsRequest::noShowMinutes,
                            RestaurantSettings::getNoShowMinutes,
                            (s, v) -> s.setNoShowMinutes((Integer) v)),
                    new Field(
                            "bufferMinutes",
                            SystemSettingsRequest::bufferMinutes,
                            RestaurantSettings::getBufferMinutes,
                            (s, v) -> s.setBufferMinutes((Integer) v)),
                    new Field(
                            "billing.serviceChargePercent",
                            r -> r.billing().serviceChargePercent().setScale(2),
                            RestaurantSettings::getServiceChargeRate,
                            (s, v) -> s.setServiceChargeRate((BigDecimal) v)),
                    new Field(
                            "billing.vatPercent",
                            r -> r.billing().vatPercent().setScale(2),
                            RestaurantSettings::getVatRate,
                            (s, v) -> s.setVatRate((BigDecimal) v)),
                    new Field(
                            "billing.bankAccount",
                            r -> text(r.billing().bankAccount()),
                            RestaurantSettings::getBankAccountNumber,
                            (s, v) -> s.setBankAccountNumber((String) v)),
                    new Field(
                            "billing.bankName",
                            r -> text(r.billing().bankName()),
                            RestaurantSettings::getBankName,
                            (s, v) -> s.setBankName((String) v)),
                    new Field(
                            "billing.bankAccountName",
                            r -> text(r.billing().bankAccountName()),
                            RestaurantSettings::getBankAccountName,
                            (s, v) -> s.setBankAccountName((String) v)),
                    new Field(
                            "timezone",
                            r -> text(r.timezone()),
                            RestaurantSettings::getTimezone,
                            (s, v) -> s.setTimezone((String) v)),
                    new Field(
                            "currency",
                            r -> text(r.currency()),
                            RestaurantSettings::getCurrency,
                            (s, v) -> s.setCurrency((String) v)),
                    new Field(
                            "emailNotifications",
                            SystemSettingsRequest::emailNotifications,
                            RestaurantSettings::isEmailNotifications,
                            (s, v) -> s.setEmailNotifications((Boolean) v)));

    private static final Set<String> RATES =
            Set.of("billing.serviceChargePercent", "billing.vatPercent");

    private final WorkspaceStore store;
    private final CurrentUserService currentUser;
    private final AuditLogRepository auditLogs;
    private final BillRepository bills;

    @Override
    public SystemSettingsResponse get(UUID actorId) {
        currentUser.require(actorId, Role.ADMIN);
        return SystemSettingsResponse.from(store.lock());
    }

    @Override
    public SystemSettingsResponse update(UUID actorId, SystemSettingsRequest req) {
        RestaurantSettings settings = store.lock();
        Employee actor = currentUser.require(actorId, Role.ADMIN);

        String timezone = text(req.timezone());
        try {
            ZoneId.of(Objects.requireNonNullElse(timezone, ""));
        } catch (DateTimeException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid timezone");
        }
        if (!"VND".equals(text(req.currency()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only VND is supported");
        }

        List<Field> changed = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        for (Field f : FIELDS) {
            Object value = f.in().apply(req);
            if (!same(f.get().apply(settings), value)) {
                changed.add(f);
                values.add(value);
            }
        }
        if (changed.isEmpty()) {
            return SystemSettingsResponse.from(settings);
        }
        if (changed.stream().anyMatch(f -> RATES.contains(f.name()))
                && bills.existsByStatus(Bill.Status.OPEN)) {
            throw ApiException.conflict("Cannot change rates while bills are open");
        }
        for (int i = 0; i < changed.size(); i++) {
            changed.get(i).set().accept(settings, values.get(i));
        }

        AuditLog log = new AuditLog();
        log.setActor(actor);
        log.setAction(AdminAuditAction.SETTINGS_UPDATED.name());
        log.setTargetType("SETTINGS");
        log.setTargetId(settings.getId());
        log.setDescription(
                "Settings updated: "
                        + String.join(", ", changed.stream().map(Field::name).toList()));
        auditLogs.save(log);
        return SystemSettingsResponse.from(settings);
    }

    private static boolean same(Object stored, Object value) {
        if (stored instanceof BigDecimal a && value instanceof BigDecimal b) {
            return a.compareTo(b) == 0;
        }
        return Objects.equals(stored, value);
    }

    private static String text(String raw) {
        return raw == null || raw.isBlank() ? null : raw.strip();
    }
}
