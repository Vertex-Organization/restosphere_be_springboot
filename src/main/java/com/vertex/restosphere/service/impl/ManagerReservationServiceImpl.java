package com.vertex.restosphere.service.impl;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.common.enums.TableStatus;
import com.vertex.restosphere.dto.request.ChangeReservationTableRequest;
import com.vertex.restosphere.dto.response.ManagerReservationResponse;
import com.vertex.restosphere.entity.AuditLog;
import com.vertex.restosphere.entity.Employee;
import com.vertex.restosphere.entity.Reservation;
import com.vertex.restosphere.entity.ReservationItem;
import com.vertex.restosphere.entity.ReservationTable;
import com.vertex.restosphere.entity.RestaurantSettings;
import com.vertex.restosphere.entity.RestaurantTable;
import com.vertex.restosphere.entity.TableSession;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.repository.AuditLogRepository;
import com.vertex.restosphere.repository.ReservationItemRepository;
import com.vertex.restosphere.repository.ReservationRepository;
import com.vertex.restosphere.repository.ReservationTableRepository;
import com.vertex.restosphere.repository.RestaurantSettingsRepository;
import com.vertex.restosphere.repository.RestaurantTableRepository;
import com.vertex.restosphere.repository.TableSessionRepository;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.ManagerReservationService;
import com.vertex.restosphere.service.ReservationWorkflow;
import com.vertex.restosphere.service.RestaurantLock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagerReservationServiceImpl implements ManagerReservationService {

    private static final Set<Reservation.Status> MOVABLE =
            Set.of(
                    Reservation.Status.PENDING,
                    Reservation.Status.CONFIRMED,
                    Reservation.Status.ARRIVED);

    private final CurrentUserService currentUser;
    private final RestaurantLock restaurantLock;
    private final ReservationWorkflow reservationWorkflow;
    private final ReservationRepository reservations;
    private final ReservationTableRepository reservationTables;
    private final ReservationItemRepository reservationItems;
    private final RestaurantTableRepository tables;
    private final TableSessionRepository sessions;
    private final RestaurantSettingsRepository settings;
    private final AuditLogRepository auditLogs;

    @Transactional(readOnly = true)
    @Override
    public List<ManagerReservationResponse> list(
            UUID actorId, LocalDate from, LocalDate to, Reservation.Status status) {
        currentUser.require(actorId, Role.ADMIN, Role.MANAGER);
        if (from != null && to != null && from.isAfter(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "from must not be after to");
        }
        ZoneId zone =
                settings.findBySingletonKey(1)
                        .map(RestaurantSettings::getTimezone)
                        .map(ZoneId::of)
                        .orElse(ZoneId.of("Asia/Ho_Chi_Minh"));
        Specification<Reservation> spec =
                (root, query, cb) -> {
                    var where = new ArrayList<jakarta.persistence.criteria.Predicate>();
                    if (from != null) {
                        where.add(
                                cb.greaterThanOrEqualTo(
                                        root.get("startsAt"), from.atStartOfDay(zone).toInstant()));
                    }
                    if (to != null) {
                        where.add(
                                cb.lessThan(
                                        root.get("startsAt"),
                                        to.plusDays(1).atStartOfDay(zone).toInstant()));
                    }
                    if (status != null) {
                        where.add(cb.equal(root.get("status"), status));
                    }
                    return cb.and(where.toArray(jakarta.persistence.criteria.Predicate[]::new));
                };
        return toResponses(reservations.findAll(spec, Sort.by("startsAt")));
    }

    @Transactional
    @Override
    public ManagerReservationResponse changeTable(
            UUID actorId, UUID id, ChangeReservationTableRequest req) {
        restaurantLock.acquire();
        Employee actor = currentUser.require(actorId, Role.ADMIN, Role.MANAGER);
        String reason = req.reason().strip();
        if (reason.length() < 5) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Reason must be at least 5 characters");
        }
        Reservation r =
                reservations.findById(id).orElseThrow(() -> ApiException.notFound("Reservation"));
        if (!MOVABLE.contains(r.getStatus())) {
            throw ApiException.conflict("Invalid reservation transition from " + r.getStatus());
        }
        RestaurantTable table =
                tables.findById(req.tableId()).orElseThrow(() -> ApiException.notFound("Table"));
        ReservationTable current = reservationWorkflow.assignment(r);
        String oldCode = current == null ? "chưa có bàn" : current.getTable().getCode();
        if (current != null && current.getTable().getId().equals(table.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a different table");
        }
        if (r.getStatus() == Reservation.Status.ARRIVED
                && (table.getStatus() != TableStatus.AVAILABLE
                        || sessions.existsByTableIdAndStatusNot(
                                table.getId(), TableSession.Status.CLOSED))) {
            throw ApiException.conflict("Table is not available now");
        }
        reservationWorkflow.assign(r, table.getId().toString());

        AuditLog log = new AuditLog();
        log.setActor(actor);
        log.setAction("RESERVATION_TABLE_CHANGED");
        log.setTargetType("RESERVATION");
        log.setTargetId(r.getId());
        log.setDescription(
                "Điều chỉnh "
                        + r.getCode()
                        + ": "
                        + oldCode
                        + " → "
                        + table.getCode()
                        + ". "
                        + reason);
        auditLogs.save(log);
        return toResponses(List.of(r)).getFirst();
    }

    private List<ManagerReservationResponse> toResponses(List<Reservation> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = list.stream().map(Reservation::getId).toList();
        Map<UUID, RestaurantTable> tableByReservation = new HashMap<>();
        reservationTables
                .findWithTableByReservationIdIn(ids)
                .forEach(
                        a ->
                                tableByReservation.putIfAbsent(
                                        a.getReservation().getId(), a.getTable()));
        Map<UUID, List<ReservationItem>> itemsByReservation =
                reservationItems.findWithMenuItemByReservationIdIn(ids).stream()
                        .collect(Collectors.groupingBy(i -> i.getReservation().getId()));
        Map<UUID, UUID> employeeByReservation = new HashMap<>();
        auditLogs
                .findActorsByReservationIdIn(ids)
                .forEach(row -> employeeByReservation.putIfAbsent((UUID) row[0], (UUID) row[1]));

        return list.stream()
                .map(
                        r -> {
                            RestaurantTable t = tableByReservation.get(r.getId());
                            UUID employee = employeeByReservation.get(r.getId());
                            List<ManagerReservationResponse.Preorder> preorder =
                                    itemsByReservation.getOrDefault(r.getId(), List.of()).stream()
                                            .map(
                                                    i ->
                                                            new ManagerReservationResponse.Preorder(
                                                                    i.getMenuItem().getId(),
                                                                    i.getMenuItem().getName(),
                                                                    i.getQuantity(),
                                                                    str(i.getNote())))
                                            .toList();
                            return new ManagerReservationResponse(
                                    r.getId(),
                                    r.getCode(),
                                    str(r.getCustomerName()),
                                    str(r.getPhone()),
                                    str(r.getEmail()),
                                    str(r.getOccasion()),
                                    r.getArea().getId(),
                                    r.getArea().getName(),
                                    r.getSource(),
                                    r.getGuestCount(),
                                    r.getActualGuests(),
                                    r.getStartsAt(),
                                    r.getEndsAt(),
                                    t == null ? null : t.getId(),
                                    t == null ? null : t.getCode(),
                                    r.getStatus(),
                                    employee == null ? "" : employee.toString(),
                                    str(r.getNote()),
                                    preorder);
                        })
                .toList();
    }

    private static String str(String s) {
        return Objects.toString(s, "");
    }
}
