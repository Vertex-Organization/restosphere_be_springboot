package com.vertex.restosphere.service;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.common.enums.TableStatus;
import com.vertex.restosphere.dto.request.TableRequest;
import com.vertex.restosphere.dto.response.ManagerTableResponse;
import com.vertex.restosphere.dto.response.TableResponse;
import com.vertex.restosphere.entity.Area;
import com.vertex.restosphere.entity.AuditLog;
import com.vertex.restosphere.entity.Reservation;
import com.vertex.restosphere.entity.RestaurantTable;
import com.vertex.restosphere.entity.TableSession;
import com.vertex.restosphere.entity.User;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.repository.AreaRepository;
import com.vertex.restosphere.repository.AuditLogRepository;
import com.vertex.restosphere.repository.ReservationTableRepository;
import com.vertex.restosphere.repository.RestaurantTableRepository;
import com.vertex.restosphere.repository.TableSessionRepository;
import com.vertex.restosphere.security.CurrentUserService;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class TableService {

    // Lich con giu ban (cung danh sach voi quy tac cu).
    private static final Set<Reservation.Status> ACTIVE_RESERVATION = EnumSet.of(Reservation.Status.PENDING,
            Reservation.Status.CONFIRMED, Reservation.Status.ARRIVED, Reservation.Status.SEATED);

    private final RestaurantTableRepository tables;
    private final AreaRepository areas;
    private final TableSessionRepository sessions;
    private final ReservationTableRepository reservationTables;
    private final RestaurantLock restaurantLock;
    private final CurrentUserService currentUser;
    private final AuditLogRepository auditLogs;

    // Khoa chung voi lenh workspace, roi hoi DB bang 2 cau exists thay vi tai het phien/lich dat.
    private void requireIdle(UUID id) {
        restaurantLock.acquire();
        if (sessions.existsByTableIdAndStatusNot(id, TableSession.Status.CLOSED)
                || reservationTables.existsByTableIdAndReservationStatusIn(id, ACTIVE_RESERVATION)) {
            throw ApiException.conflict("Table has an active session or reservation; use workspace commands");
        }
    }

    @Transactional(readOnly = true)
    public List<ManagerTableResponse> listForManager(UUID actorId) {
        currentUser.require(actorId, Role.ADMIN, Role.MANAGER);
        Map<UUID, UUID> sessionByTable = new HashMap<>();
        sessions.findOpenSessionIdsByTable().forEach(r -> sessionByTable.putIfAbsent((UUID) r[0], (UUID) r[1]));
        Set<UUID> reserved = reservationTables.findTableIdsByReservationStatusIn(ACTIVE_RESERVATION);
        return tables.findAllByOrderByAreaNameAscCodeAsc().stream()
                .map(t -> new ManagerTableResponse(t.getId(), t.getCode(), t.getArea().getId(), t.getArea().getName(),
                        t.getCapacity(), t.getStatus(), sessionByTable.get(t.getId()), reserved.contains(t.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TableResponse> list(UUID areaId, TableStatus status) {
        List<RestaurantTable> result = areaId != null && status != null ? tables.findByAreaIdAndStatus(areaId, status)
                : areaId != null ? tables.findByAreaId(areaId)
                : status != null ? tables.findByStatus(status)
                : tables.findAll();
        return result.stream().map(TableResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public TableResponse get(UUID id) {
        return TableResponse.from(find(id));
    }

    public TableResponse create(TableRequest req) {
        String code = req.code().trim();
        if (tables.existsByCode(code)) {
            throw ApiException.conflict("Table code already exists");
        }
        RestaurantTable table = new RestaurantTable();
        apply(table, code, req);
        return TableResponse.from(tables.save(table));
    }

    public TableResponse update(UUID actorId, UUID id, TableRequest req) {
        requireIdle(id);
        User actor = currentUser.require(actorId, Role.ADMIN, Role.MANAGER);
        RestaurantTable table = find(id);
        // Chi chan o day (khong dua vao requireIdle) de PATCH /status va DELETE giu hanh vi cu.
        if (table.getStatus() == TableStatus.OCCUPIED || table.getStatus() == TableStatus.CLEANING) {
            throw ApiException.conflict("Table is in service or being cleaned");
        }
        String code = req.code().trim();
        if (!code.equals(table.getCode()) && tables.existsByCode(code)) {
            throw ApiException.conflict("Table code already exists");
        }
        Area area = areas.findById(req.areaId()).orElseThrow(() -> ApiException.notFound("Area"));
        TableStatus status = req.outOfService() == null ? table.getStatus()
                : req.outOfService() ? TableStatus.OUT_OF_SERVICE : TableStatus.AVAILABLE;
        if (code.equals(table.getCode()) && area.getId().equals(table.getArea().getId())
                && req.capacity() == table.getCapacity() && status == table.getStatus()) {
            return TableResponse.from(table);
        }
        table.setCode(code);
        table.setArea(area);
        table.setCapacity(req.capacity());
        table.setStatus(status);
        AuditLog log = new AuditLog();
        log.setActor(actor);
        log.setAction("TABLE_UPDATED");
        log.setTargetType("TABLE");
        log.setTargetId(id);
        log.setDescription("Cập nhật bố trí bàn " + code + " · " + req.capacity() + " ghế · " + area.getName()
                + (status == TableStatus.OUT_OF_SERVICE ? " · tạm ngưng" : ""));
        auditLogs.save(log);
        return TableResponse.from(table);
    }

    public TableResponse updateStatus(UUID id, TableStatus status) {
        requireIdle(id);
        RestaurantTable table = find(id);
        table.setStatus(status);
        return TableResponse.from(table);
    }

    public void delete(UUID id) {
        requireIdle(id);
        tables.delete(find(id));
    }

    private void apply(RestaurantTable table, String code, TableRequest req) {
        table.setCode(code);
        table.setCapacity(req.capacity());
        table.setArea(areas.findById(req.areaId()).orElseThrow(() -> ApiException.notFound("Area")));
    }

    private RestaurantTable find(UUID id) {
        return tables.findById(id).orElseThrow(() -> ApiException.notFound("Table"));
    }
}
