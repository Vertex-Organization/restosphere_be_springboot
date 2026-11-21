package com.vertex.restosphere.service.impl;

import static com.vertex.restosphere.common.util.Values.id;
import static com.vertex.restosphere.common.util.Values.invalid;
import static com.vertex.restosphere.common.util.Values.map;
import static com.vertex.restosphere.common.util.Values.object;
import static com.vertex.restosphere.common.util.Values.required;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.entity.ApprovalRequest;
import com.vertex.restosphere.entity.Reservation;
import com.vertex.restosphere.entity.User;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.service.BillingWorkflow;
import com.vertex.restosphere.service.ReservationWorkflow;
import com.vertex.restosphere.service.ServiceWorkflow;
import com.vertex.restosphere.service.WaiterService;
import com.vertex.restosphere.service.WorkspaceService;
import com.vertex.restosphere.service.WorkspaceSnapshots;
import com.vertex.restosphere.service.WorkspaceStore;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Workspace của nhân viên: đọc snapshot cho bếp, thu ngân, lễ tân/phục vụ và nhận lệnh của lễ tân
 * và phục vụ (mobile). Lệnh của bếp, thu ngân, quản lý, quản trị đi qua REST riêng.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class WorkspaceServiceImpl implements WorkspaceService {

    private static final Set<String> READ_COMMANDS =
            Set.of("read", "read-notification", "read-notifications", "notification.read");

    private static final Set<String> RECEPTION_COMMANDS =
            Set.of(
                    "create",
                    "edit",
                    "confirm",
                    "reject",
                    "cancel",
                    "no-show",
                    "assign",
                    "check-in",
                    "handover",
                    "request-exception");

    private static final Map<String, String> SUMMARIES =
            Map.ofEntries(
                    Map.entry("create", "Đã tạo lịch đặt."),
                    Map.entry("edit", "Đã cập nhật lịch đặt."),
                    Map.entry("confirm", "Đã xác nhận và giữ bàn."),
                    Map.entry("reject", "Đã từ chối lịch đặt."),
                    Map.entry("cancel", "Đã hủy lịch đặt."),
                    Map.entry("no-show", "Đã đánh dấu khách không đến."),
                    Map.entry("assign", "Đã cập nhật bàn."),
                    Map.entry("check-in", "Đã ghi nhận khách đến."),
                    Map.entry("handover", "Đã bàn giao cho phục vụ."),
                    Map.entry("request-exception", "Đã gửi yêu cầu tới quản lý."));

    private final WorkspaceStore db;
    private final WorkspaceSnapshots snapshots;
    private final ReservationWorkflow reservations;
    private final ServiceWorkflow service;
    private final BillingWorkflow billing;
    private final WaiterService waiter;

    @Override
    public Map<String, Object> load(String workspace, String subject) {
        db.lock();
        return snapshots.snapshot(workspace, authorize(workspace, subject));
    }

    @Override
    public Map<String, Object> waiter(String subject, String action, Consumer<User> operation) {
        var settings = db.lock();
        var user = authorize("staff", subject);
        if (user.getRole() != Role.ADMIN && user.getRole() != Role.WAITER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Command not permitted for your role");
        }
        operation.accept(user);
        settings.setRevision(settings.getRevision() + 1);
        service.audit(user, action, user.getId(), "WORKSPACE", action);
        db.flush();
        return snapshots.snapshot("staff", user);
    }

    @Override
    public Map<String, Object> command(String subject, Map<String, Object> c) {
        var settings = db.lock();
        var user = authorize("staff", subject);
        String type = required(c, "type", 80);
        String reservationId = null;
        if (READ_COMMANDS.contains(type)) {
            service.read(c, user);
        } else {
            boolean reception = RECEPTION_COMMANDS.contains(type);
            requireStaffRole(user, reception);
            if (type.equals("create")) {
                var r =
                        reservations.create(
                                object(c, "input"),
                                user,
                                false,
                                Boolean.TRUE.equals(c.get("walkIn")));
                reservationId = id(r);
            } else if (type.equals("request-exception")) {
                requestException(c, user);
            } else if (reception) {
                reservations.command(type, c, user);
            } else {
                waiter.handle(type, c, user);
            }
        }
        settings.setRevision(settings.getRevision() + 1);
        service.audit(user, type, user.getId(), "WORKSPACE", type);
        db.flush();
        var data = snapshots.snapshot("staff", user);
        return map(
                "data",
                data,
                "state",
                data,
                "summary",
                SUMMARIES.getOrDefault(type, "Đã lưu thay đổi."),
                "ref",
                reservationId,
                "reservationId",
                reservationId);
    }

    private void requestException(Map<String, Object> c, User user) {
        var r = db.get(Reservation.class, required(c, "id", 100));
        String reason = required(c, "reason", 200);
        if (reason.length() < 5) {
            throw invalid("Lý do cần từ 5 đến 200 ký tự.");
        }
        var allowed =
                Set.of(
                        Reservation.Status.PENDING,
                        Reservation.Status.CONFIRMED,
                        Reservation.Status.ARRIVED);
        if (!allowed.contains(r.getStatus())) {
            throw ApiException.conflict("Lịch ở trạng thái này không thể gửi yêu cầu.");
        }
        var request =
                billing.request(
                        ApprovalRequest.Type.RESERVATION,
                        "RESERVATION",
                        r.getId(),
                        BigDecimal.ZERO,
                        reason,
                        user);
        reservations.history(
                r, user, "request-exception", "Gửi yêu cầu phê duyệt tới quản lý: " + reason);
        service.notify(
                Role.MANAGER,
                "APPROVAL",
                "Yêu cầu phê duyệt đặt bàn " + r.getCode(),
                reason,
                id(request));
    }

    private static void requireStaffRole(User user, boolean reception) {
        if (user.getRole() == Role.ADMIN) {
            return;
        }
        if (user.getRole() != (reception ? Role.RECEPTIONIST : Role.WAITER)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "Tài khoản của bạn không có quyền thực hiện thao tác này.");
        }
    }

    private User authorize(String workspace, String subject) {
        User user;
        try {
            user = db.get(User.class, subject);
        } catch (ApiException e) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Account unavailable");
        }
        Set<Role> allowed =
                switch (workspace) {
                    case "kitchen" -> Set.of(Role.KITCHEN, Role.ADMIN);
                    case "cashier" -> Set.of(Role.CASHIER, Role.ADMIN);
                    case "staff" -> Set.of(Role.RECEPTIONIST, Role.WAITER, Role.ADMIN);
                    default -> throw ApiException.notFound("Workspace");
                };
        if (!user.isEnabled() || !allowed.contains(user.getRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Workspace access denied");
        }
        return user;
    }
}
