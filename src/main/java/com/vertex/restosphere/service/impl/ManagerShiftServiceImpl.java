package com.vertex.restosphere.service.impl;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.dto.request.ShiftRequest;
import com.vertex.restosphere.dto.response.ShiftResponse;
import com.vertex.restosphere.entity.AuditLog;
import com.vertex.restosphere.entity.RestaurantSettings;
import com.vertex.restosphere.entity.Shift;
import com.vertex.restosphere.entity.ShiftAssignment;
import com.vertex.restosphere.entity.User;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.repository.AuditLogRepository;
import com.vertex.restosphere.repository.RestaurantSettingsRepository;
import com.vertex.restosphere.repository.ShiftAssignmentRepository;
import com.vertex.restosphere.repository.ShiftRepository;
import com.vertex.restosphere.repository.UserRepository;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.ManagerShiftService;
import com.vertex.restosphere.service.RestaurantLock;
import jakarta.persistence.criteria.Predicate;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
public class ManagerShiftServiceImpl implements ManagerShiftService {

    private static final Duration MAX_PER_DAY = Duration.ofHours(12);
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM");

    private final CurrentUserService currentUser;
    private final RestaurantLock restaurantLock;
    private final ShiftRepository shifts;
    private final ShiftAssignmentRepository assignments;
    private final UserRepository users;
    private final RestaurantSettingsRepository settings;
    private final AuditLogRepository auditLogs;

    @Transactional(readOnly = true)
    @Override
    public List<ShiftResponse> list(UUID actorId, LocalDate from, LocalDate to) {
        currentUser.require(actorId, Role.ADMIN, Role.MANAGER);
        if (from != null && to != null && from.isAfter(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "from must not be after to");
        }
        ZoneId zone = zone();
        Specification<Shift> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (from != null) {
                where.add(cb.greaterThanOrEqualTo(root.get("startsAt"), from.atStartOfDay(zone).toInstant()));
            }
            if (to != null) {
                where.add(cb.lessThan(root.get("startsAt"), to.plusDays(1).atStartOfDay(zone).toInstant()));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        List<Shift> list = shifts.findAll(spec, Sort.by("startsAt"));
        if (list.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<UUID>> staff = assignments.findWithUserByShiftIdIn(list.stream().map(Shift::getId).toList())
                .stream().collect(Collectors.groupingBy(a -> a.getShift().getId(),
                        Collectors.mapping(a -> a.getUser().getId(), Collectors.toList())));
        return list.stream().map(s -> toResponse(s, staff.getOrDefault(s.getId(), List.of()), zone)).toList();
    }

    @Transactional
    @Override
    public ShiftResponse create(UUID actorId, ShiftRequest req) {
        return save(actorId, null, req);
    }

    @Transactional
    @Override
    public ShiftResponse update(UUID actorId, UUID id, ShiftRequest req) {
        return save(actorId, id, req);
    }

    private ShiftResponse save(UUID actorId, UUID id, ShiftRequest req) {
        restaurantLock.acquire();
        User actor = currentUser.require(actorId, Role.ADMIN, Role.MANAGER);
        ZoneId zone = zone();
        String name = req.name().strip();
        LocalDate date;
        LocalTime start;
        LocalTime end;
        try {
            date = LocalDate.parse(req.date());
            start = LocalTime.parse(req.start());
            end = LocalTime.parse(req.end());
        } catch (DateTimeException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid shift time");
        }
        if (!end.isAfter(start)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Shift must end after start");
        }
        Instant startsAt = date.atTime(start).atZone(zone).toInstant();
        Instant endsAt = date.atTime(end).atZone(zone).toInstant();
        Duration length = Duration.between(startsAt, endsAt);
        if (length.compareTo(MAX_PER_DAY) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Shift cannot exceed 12 hours");
        }
        Shift shift = id == null ? new Shift() : shifts.findById(id).orElseThrow(() -> ApiException.notFound("Shift"));

        List<User> staff = employees(req.employeeIds());
        Instant dayStart = date.atStartOfDay(zone).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant();
        Map<UUID, Duration> worked = new HashMap<>();
        for (ShiftAssignment a : assignments.findByUsersBetween(req.employeeIds(), dayStart, dayEnd)) {
            Shift other = a.getShift();
            if (other.getId().equals(id)) {
                continue;
            }
            if (other.getStartsAt().isBefore(endsAt) && other.getEndsAt().isAfter(startsAt)) {
                throw ApiException.conflict("Employee already assigned to an overlapping shift");
            }
            Instant from = other.getStartsAt().isAfter(dayStart) ? other.getStartsAt() : dayStart;
            Instant to = other.getEndsAt().isBefore(dayEnd) ? other.getEndsAt() : dayEnd;
            worked.merge(a.getUser().getId(), Duration.between(from, to), Duration::plus);
        }
        if (worked.values().stream().anyMatch(d -> d.plus(length).compareTo(MAX_PER_DAY) > 0)) {
            throw ApiException.conflict("Employee exceeds 12 hours per day");
        }

        Set<UUID> newStaff = new HashSet<>(req.employeeIds());
        if (id != null && name.equals(shift.getName()) && startsAt.equals(shift.getStartsAt())
                && endsAt.equals(shift.getEndsAt()) && req.minimum() == shift.getMinimumStaff()
                && newStaff.equals(currentStaff(id))) {
            return toResponse(shift, req.employeeIds(), zone);
        }
        shift.setName(name);
        shift.setStartsAt(startsAt);
        shift.setEndsAt(endsAt);
        shift.setMinimumStaff(req.minimum());
        shifts.save(shift);
        if (id != null) {
            assignments.deleteByShiftId(id);
        }
        for (User u : staff) {
            ShiftAssignment a = new ShiftAssignment();
            a.setShift(shift);
            a.setUser(u);
            assignments.save(a);
        }

        AuditLog log = new AuditLog();
        log.setActor(actor);
        log.setAction(id == null ? "SHIFT_CREATED" : "SHIFT_UPDATED");
        log.setTargetType("SHIFT");
        log.setTargetId(shift.getId());
        log.setDescription((id == null ? "Tạo ca " : "Cập nhật ca ") + name + " " + date.format(DAY_MONTH) + " ("
                + start.format(HH_MM) + "–" + end.format(HH_MM) + "), " + staff.size() + "/" + req.minimum()
                + " nhân viên");
        auditLogs.save(log);
        return toResponse(shift, req.employeeIds(), zone);
    }

    private List<User> employees(List<UUID> ids) {
        if (new LinkedHashSet<>(ids).size() != ids.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Duplicate employee");
        }
        List<User> found = users.findAllById(ids);
        if (found.size() != ids.size() || found.stream().anyMatch(u -> u.getRole() == Role.CUSTOMER)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid employee");
        }
        if (found.stream().anyMatch(u -> !u.isEnabled())) {
            throw ApiException.conflict("Employee is inactive");
        }
        return found;
    }

    private Set<UUID> currentStaff(UUID shiftId) {
        return assignments.findWithUserByShiftIdIn(List.of(shiftId)).stream().map(a -> a.getUser().getId())
                .collect(Collectors.toSet());
    }

    private ZoneId zone() {
        return settings.findBySingletonKey(1).map(RestaurantSettings::getTimezone).map(ZoneId::of)
                .orElse(ZoneId.of("Asia/Ho_Chi_Minh"));
    }

    private static ShiftResponse toResponse(Shift s, List<UUID> employeeIds, ZoneId zone) {
        var start = s.getStartsAt().atZone(zone);
        return new ShiftResponse(s.getId(), s.getName(), start.toLocalDate(), start.format(HH_MM),
                s.getEndsAt().atZone(zone).format(HH_MM), s.getMinimumStaff(), employeeIds);
    }
}
