package com.vertex.restosphere.service.impl;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.dto.request.AssignEmployeeAreaRequest;
import com.vertex.restosphere.dto.response.ManagerEmployeeResponse;
import com.vertex.restosphere.entity.Area;
import com.vertex.restosphere.entity.AuditLog;
import com.vertex.restosphere.entity.User;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.repository.AreaRepository;
import com.vertex.restosphere.repository.AuditLogRepository;
import com.vertex.restosphere.repository.UserRepository;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.ManagerEmployeeService;
import com.vertex.restosphere.service.RestaurantLock;
import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagerEmployeeServiceImpl implements ManagerEmployeeService {

    private static final Comparator<ManagerEmployeeResponse> ORDER = Comparator
            .comparing(ManagerEmployeeResponse::department)
            .thenComparing(ManagerEmployeeResponse::name, Collator.getInstance(Locale.of("vi")));

    private final CurrentUserService currentUser;
    private final RestaurantLock restaurantLock;
    private final UserRepository users;
    private final AreaRepository areas;
    private final AuditLogRepository auditLogs;

    @Transactional(readOnly = true)
    @Override
    public List<ManagerEmployeeResponse> list(UUID actorId) {
        currentUser.require(actorId, Role.ADMIN, Role.MANAGER);
        return users.findByRoleNot(Role.CUSTOMER).stream().map(ManagerEmployeeResponse::from).sorted(ORDER).toList();
    }

    @Transactional
    @Override
    public ManagerEmployeeResponse assignArea(UUID actorId, UUID id, AssignEmployeeAreaRequest req) {
        restaurantLock.acquire();
        User actor = currentUser.require(actorId, Role.ADMIN, Role.MANAGER);
        User employee = users.findById(id).orElseThrow(() -> ApiException.notFound("Employee"));
        if (employee.getRole() == Role.CUSTOMER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Not an employee");
        }
        if (!employee.isEnabled()) {
            throw ApiException.conflict("Employee is inactive");
        }
        Area area = areas.findById(req.areaId()).orElseThrow(() -> ApiException.notFound("Area"));
        if (employee.getAssignedArea() != null && employee.getAssignedArea().getId().equals(area.getId())) {
            return ManagerEmployeeResponse.from(employee);
        }
        employee.setAssignedArea(area);

        AuditLog log = new AuditLog();
        log.setActor(actor);
        log.setAction("EMPLOYEE_AREA_ASSIGNED");
        log.setTargetType("USER");
        log.setTargetId(employee.getId());
        log.setDescription("Phân công " + employee.getFullName() + " vào khu " + area.getName());
        auditLogs.save(log);
        return ManagerEmployeeResponse.from(employee);
    }
}
