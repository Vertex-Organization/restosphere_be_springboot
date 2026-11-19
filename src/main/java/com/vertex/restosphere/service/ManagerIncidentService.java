package com.vertex.restosphere.service;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.dto.request.ManagerIncidentUpdateRequest;
import com.vertex.restosphere.dto.response.ManagerIncidentResponse;
import com.vertex.restosphere.entity.AuditLog;
import com.vertex.restosphere.entity.Incident;
import com.vertex.restosphere.entity.User;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.repository.AuditLogRepository;
import com.vertex.restosphere.repository.IncidentRepository;
import com.vertex.restosphere.repository.UserRepository;
import com.vertex.restosphere.security.CurrentUserService;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Su co bep cho trang Manager; thay lenh cu "incident.update" (lenh cu van giu). */
@Service
@RequiredArgsConstructor
public class ManagerIncidentService {

    private static final int AUDIT_MAX = 4000;
    private static final Set<Incident.Status> CLOSED = Set.of(Incident.Status.RESOLVED, Incident.Status.CLOSED);

    private final CurrentUserService currentUser;
    private final RestaurantLock restaurantLock;
    private final IncidentRepository incidents;
    private final UserRepository users;
    private final ServiceWorkflow serviceWorkflow;
    private final AuditLogRepository auditLogs;

    @Transactional(readOnly = true)
    public List<ManagerIncidentResponse> list(UUID actorId) {
        currentUser.require(actorId, Role.ADMIN, Role.MANAGER);
        return incidents.findAllByOrderByCreatedAtDesc().stream().map(ManagerIncidentResponse::from).toList();
    }

    @Transactional
    public ManagerIncidentResponse update(UUID actorId, UUID id, ManagerIncidentUpdateRequest req) {
        restaurantLock.acquire();
        User actor = currentUser.require(actorId, Role.ADMIN, Role.MANAGER);
        Incident i = incidents.findWithDetailsById(id).orElseThrow(() -> ApiException.notFound("Incident"));
        if (CLOSED.contains(i.getStatus())) {
            throw ApiException.conflict("Incident is already closed");
        }
        // CLOSED chi con trong du lieu cu, Manager dong su co bang RESOLVED.
        if (req.status() == Incident.Status.CLOSED) {
            throw bad("Status must be OPEN, ACKNOWLEDGED or RESOLVED");
        }
        String reply = req.reply() == null || req.reply().isBlank() ? null : req.reply().strip();
        if (reply != null && reply.length() < 5) {
            throw bad("Reply must be at least 5 characters");
        }
        User oldAssignee = i.getAssignee();
        User assignee = oldAssignee;
        if (req.assigneeId() != null) {
            // 400 (khong 404) de FE bao loi o o nguoi xu ly.
            assignee = users.findById(req.assigneeId())
                    .filter(u -> u.isEnabled() && u.getRole() != Role.CUSTOMER)
                    .orElseThrow(() -> bad("Assignee must be an active employee"));
        }
        if (req.status() == Incident.Status.RESOLVED) {
            if (assignee == null || !assignee.isEnabled()) {
                throw bad("Resolved incident needs an active assignee");
            }
            if (reply == null) {
                throw bad("Resolution is required");
            }
        }

        boolean assigneeChanged = !sameUser(oldAssignee, assignee);
        boolean replyChanged = reply != null && !reply.equals(i.getResolution());
        Incident.Status oldStatus = i.getStatus();
        if (!assigneeChanged && oldStatus == req.status() && !replyChanged) {
            return ManagerIncidentResponse.from(i);
        }

        i.setAssignee(assignee);
        i.setStatus(req.status());
        if (reply != null) {
            i.setResolution(reply);
        }
        if (req.status() == Incident.Status.RESOLVED) {
            i.setResolvedAt(Instant.now());
        }
        // Giong lenh cu: bep nhan phan hoi (hoac trang thai moi).
        serviceWorkflow.notify(Role.KITCHEN, "INCIDENT_REPLY", "Incident updated",
                reply != null ? reply : req.status().name(), i.getId().toString());

        StringBuilder text = new StringBuilder("Cập nhật sự cố ").append(ManagerIncidentResponse.code(i))
                .append(" (").append(typeName(i.getType())).append("): ").append(statusName(oldStatus))
                .append(" → ").append(statusName(req.status()));
        if (assigneeChanged) {
            text.append(" · giao cho ").append(assignee.getFullName());
        }
        if (replyChanged) {
            text.append(" · phản hồi: ").append(reply);
        }
        AuditLog log = new AuditLog();
        log.setActor(actor);
        log.setAction("INCIDENT_UPDATED");
        log.setTargetType("INCIDENT");
        log.setTargetId(i.getId());
        log.setDescription(text.length() > AUDIT_MAX ? text.substring(0, AUDIT_MAX) : text.toString());
        auditLogs.save(log);
        // Flush de updatedAt (response.at) la gio vua sua.
        incidents.flush();
        return ManagerIncidentResponse.from(i);
    }

    private static boolean sameUser(User a, User b) {
        return Objects.equals(a == null ? null : a.getId(), b == null ? null : b.getId());
    }

    private static ApiException bad(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    private static String statusName(Incident.Status s) {
        return switch (s) {
            case OPEN -> "Chưa xử lý";
            case ACKNOWLEDGED -> "Đã tiếp nhận";
            case RESOLVED, CLOSED -> "Đã đóng";
        };
    }

    private static String typeName(String type) {
        return switch (type) {
            case "OUT_OF_STOCK" -> "Hết nguyên liệu";
            case "EQUIPMENT" -> "Thiết bị hỏng";
            case "CANNOT_PREPARE" -> "Không thể chế biến";
            case "NOTE_CLARIFICATION" -> "Cần xác nhận ghi chú";
            case "OTHER" -> "Sự cố khác";
            default -> type;
        };
    }
}
