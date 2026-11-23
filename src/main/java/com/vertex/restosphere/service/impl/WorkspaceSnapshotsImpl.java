package com.vertex.restosphere.service.impl;

import static com.vertex.restosphere.common.util.Values.id;
import static com.vertex.restosphere.common.util.Values.map;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.entity.*;
import com.vertex.restosphere.mapper.ApprovalMapper;
import com.vertex.restosphere.mapper.BillMapper;
import com.vertex.restosphere.mapper.CashierMapper;
import com.vertex.restosphere.mapper.IncidentMapper;
import com.vertex.restosphere.mapper.MenuMapper;
import com.vertex.restosphere.mapper.NotificationMapper;
import com.vertex.restosphere.mapper.OrderMapper;
import com.vertex.restosphere.mapper.ReservationMapper;
import com.vertex.restosphere.mapper.StaffMapper;
import com.vertex.restosphere.mapper.TableMapper;
import com.vertex.restosphere.service.ReservationWorkflow;
import com.vertex.restosphere.service.WorkspaceSnapshots;
import com.vertex.restosphere.service.WorkspaceStore;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Dựng snapshot JSON cho từng workspace (kitchen, cashier, staff). Mỗi workspace chỉ nhận các khóa
 * trong {@link #FIELDS}; việc chuyển entity sang Map nằm ở package {@code mapper}.
 */
@Service
@RequiredArgsConstructor
public class WorkspaceSnapshotsImpl implements WorkspaceSnapshots {

    private static final Set<ApprovalRequest.Type> CASHIER_REQUESTS =
            Set.of(
                    ApprovalRequest.Type.DISCOUNT,
                    ApprovalRequest.Type.BILL_ADJUSTMENT,
                    ApprovalRequest.Type.PAYMENT_VOID);

    private static final Map<String, Set<String>> FIELDS =
            Map.of(
                    "kitchen",
                    Set.of(
                            "schema",
                            "seedDay",
                            "revision",
                            "nextId",
                            "nextTicket",
                            "staff",
                            "orders",
                            "items",
                            "menuCategories",
                            "menuItems",
                            "menuRequests",
                            "incidents",
                            "notifications",
                            "outbox"),
                    "cashier",
                    Set.of(
                            "schema",
                            "seedDay",
                            "revision",
                            "nextId",
                            "counters",
                            "config",
                            "staff",
                            "menu",
                            "tables",
                            "sessions",
                            "orders",
                            "items",
                            "bills",
                            "payments",
                            "requests",
                            "shifts",
                            "scheduledShifts",
                            "notifications",
                            "outbox",
                            "processedKeys"),
                    "staff",
                    Set.of(
                            "version",
                            "serviceVersion",
                            "revision",
                            "settings",
                            "seedDay",
                            "areas",
                            "tables",
                            "reservations",
                            "notifications",
                            "assignments",
                            "nextId",
                            "employees",
                            "tableSessions",
                            "menuCategories",
                            "menuItems",
                            "orders",
                            "orderItems",
                            "drafts",
                            "bills",
                            "waiterNotifications",
                            "requests",
                            "outbox"));

    private final WorkspaceStore db;
    private final ReservationWorkflow reservations;
    private final BillMapper bills;
    private final OrderMapper orders;
    private final MenuMapper menus;
    private final TableMapper tables;
    private final ReservationMapper reservationMapper;
    private final ApprovalMapper approvals;
    private final NotificationMapper notifications;
    private final StaffMapper staffMapper;
    private final CashierMapper cashierMapper;
    private final IncidentMapper incidents;

    @Override
    public Map<String, Object> snapshot(String workspace, Employee user) {
        var settings = db.lock();
        db.flush();
        var ctx = new SnapshotContext(db, settings, user);
        var fields = FIELDS.getOrDefault(workspace, FIELDS.get("staff"));
        Map<String, Object> data = new LinkedHashMap<>();
        BiConsumer<String, Supplier<Object>> put =
                (key, value) -> {
                    if (fields.contains(key)) {
                        data.put(key, value.get());
                    }
                };
        boolean cashier = workspace.equals("cashier");

        put.accept("schema", () -> 1);
        put.accept("version", () -> 1);
        put.accept("serviceVersion", () -> 1);
        put.accept("revision", settings::getRevision);
        put.accept("seedDay", () -> LocalDate.now(ctx.zone));
        put.accept("nextId", () -> settings.getRevision() + 1);
        put.accept("nextTicket", () -> ctx.all(Order.class).size() + 1);
        put.accept("settings", reservations::settings);

        put.accept("staff", () -> staff(ctx, workspace));
        put.accept("employees", () -> staff(ctx, workspace));
        put.accept("areas", () -> ctx.all(Area.class).stream().map(tables::toMap).toList());
        put.accept("tables", () -> tableList(ctx));
        put.accept("sessions", () -> sessions(ctx));
        put.accept("tableSessions", () -> sessions(ctx));
        put.accept("assignments", () -> assignments(ctx));
        put.accept("reservations", () -> reservationList(ctx));

        put.accept("menuCategories", () -> categories(ctx));
        put.accept("menu", () -> menu(ctx));
        put.accept("menuItems", () -> menu(ctx));
        put.accept("menuRequests", () -> menuRequests(ctx));

        put.accept("orders", () -> orderList(ctx));
        put.accept("items", () -> items(ctx));
        put.accept("orderItems", () -> items(ctx));
        put.accept("drafts", () -> drafts(ctx));
        put.accept("bills", () -> billList(ctx));
        put.accept("payments", () -> paymentList(ctx));
        put.accept("requests", () -> requests(ctx, cashier));
        put.accept("incidents", () -> incidentList(ctx));

        put.accept("notifications", () -> notificationList(ctx));
        put.accept("waiterNotifications", () -> notificationList(ctx));
        put.accept("outbox", () -> notificationList(ctx));

        put.accept("shifts", () -> shifts(ctx));
        put.accept("scheduledShifts", () -> scheduledShifts(ctx));
        put.accept("config", () -> cashierMapper.toConfigMap(settings));
        put.accept("counters", () -> counters(ctx));
        put.accept("processedKeys", () -> processedKeys(ctx));
        return data;
    }

    private List<Map<String, Object>> staff(SnapshotContext ctx, String workspace) {
        return ctx.all(Employee.class).stream()
                .map(
                        u ->
                                staffMapper.toMap(
                                        u, workspace, ctx.settings, ctx.assignmentsOf(u), ctx.now))
                .toList();
    }

    private List<Map<String, Object>> tableList(SnapshotContext ctx) {
        return ctx.all(RestaurantTable.class).stream()
                .map(t -> tables.toMap(t, ctx.openSession(t)))
                .toList();
    }

    private List<Map<String, Object>> sessions(SnapshotContext ctx) {
        return ctx.all(TableSession.class).stream().map(tables::toMap).toList();
    }

    private List<Map<String, Object>> assignments(SnapshotContext ctx) {
        return ctx.all(ReservationTable.class).stream()
                .map(a -> tables.toMap(a, reservations.active(a.getReservation())))
                .toList();
    }

    private List<Map<String, Object>> reservationList(SnapshotContext ctx) {
        var assignment =
                SnapshotContext.first(
                        ctx.all(ReservationTable.class), a -> a.getReservation().getId());
        var preorder =
                SnapshotContext.group(
                        ctx.all(ReservationItem.class), i -> i.getReservation().getId());
        return ctx.all(Reservation.class).stream()
                .map(
                        r ->
                                reservationMapper.toStaffMap(
                                        r,
                                        assignment.get(r.getId()),
                                        preorder.getOrDefault(r.getId(), List.of()),
                                        ctx.audit(r.getId()).stream()
                                                .sorted(ReservationMapper.HISTORY_ORDER)
                                                .toList()))
                .toList();
    }

    private List<Map<String, Object>> categories(SnapshotContext ctx) {
        return ctx.all(MenuCategory.class).stream().map(menus::toMap).toList();
    }

    private List<Map<String, Object>> menu(SnapshotContext ctx) {
        return ctx.all(MenuItem.class).stream().map(menus::toMap).toList();
    }

    private List<Map<String, Object>> menuRequests(SnapshotContext ctx) {
        return ctx.all(ApprovalRequest.class).stream()
                .filter(r -> r.getType() == ApprovalRequest.Type.MENU_STATUS)
                .map(menus::toRequestMap)
                .toList();
    }

    private List<Map<String, Object>> orderList(SnapshotContext ctx) {
        return ctx.all(Order.class).stream()
                .map(
                        o ->
                                orders.toMap(
                                        o,
                                        ctx.bill(o.getSession()),
                                        ctx.itemsOf(o),
                                        i -> ctx.audit(i.getId())))
                .toList();
    }

    private List<Map<String, Object>> items(SnapshotContext ctx) {
        return ctx.all(OrderItem.class).stream()
                .map(i -> orders.toMap(i, ctx.audit(i.getId())))
                .toList();
    }

    private List<Map<String, Object>> drafts(SnapshotContext ctx) {
        return ctx.all(OrderDraft.class).stream()
                .filter(d -> ctx.mine(d.getWaiter()))
                .map(orders::toMap)
                .toList();
    }

    private List<Map<String, Object>> billList(SnapshotContext ctx) {
        return ctx.all(Bill.class).stream().map(b -> bills.toMap(b, ctx.audit(b.getId()))).toList();
    }

    private List<Map<String, Object>> paymentList(SnapshotContext ctx) {
        return ctx.all(Payment.class).stream().map(bills::toMap).toList();
    }

    private List<Map<String, Object>> requests(SnapshotContext ctx, boolean cashier) {
        return ctx.all(ApprovalRequest.class).stream()
                .filter(r -> cashier ? CASHIER_REQUESTS.contains(r.getType()) : isStaffRequest(r))
                .map(r -> approval(ctx, r))
                .toList();
    }

    private static boolean isStaffRequest(ApprovalRequest r) {
        return r.getType() == ApprovalRequest.Type.CANCEL_ITEM
                || r.getType() == ApprovalRequest.Type.RESERVATION;
    }

    private Map<String, Object> approval(SnapshotContext ctx, ApprovalRequest r) {
        Bill bill = null;
        OrderItem item = null;
        Payment payment = null;
        switch (r.getTargetType()) {
            case "BILL" -> bill = db.get(Bill.class, r.getTargetId().toString());
            case "ORDER_ITEM" -> {
                item = db.get(OrderItem.class, r.getTargetId().toString());
                bill = ctx.bill(item.getOrder().getSession());
            }
            case "PAYMENT" -> {
                payment = db.get(Payment.class, r.getTargetId().toString());
                bill = payment.getBill();
            }
            default -> {}
        }
        return approvals.toMap(r, bill, item, payment);
    }

    private List<Map<String, Object>> incidentList(SnapshotContext ctx) {
        var managerIds =
                ctx.all(Employee.class).stream()
                        .filter(u -> u.getRole() == Role.MANAGER)
                        .map(u -> id(u))
                        .toList();
        return ctx.all(Incident.class).stream().map(i -> incidents.toMap(i, managerIds)).toList();
    }

    private List<Map<String, Object>> notificationList(SnapshotContext ctx) {
        return ctx.all(Notification.class).stream()
                .filter(
                        n ->
                                n.getAudienceRole() == ctx.user.getRole()
                                        || ctx.mine(n.getRecipient()))
                .map(n -> notifications.toMap(n, ctx.readsOf(n)))
                .toList();
    }

    private List<Map<String, Object>> shifts(SnapshotContext ctx) {
        return ctx.all(CashierShift.class).stream().map(cashierMapper::toMap).toList();
    }

    private List<Map<String, Object>> scheduledShifts(SnapshotContext ctx) {
        return ctx.all(ShiftAssignment.class).stream()
                .filter(a -> a.getEmployee().getRole() == Role.CASHIER)
                .map(cashierMapper::toScheduledMap)
                .toList();
    }

    private Map<String, Object> counters(SnapshotContext ctx) {
        return map(
                "bill",
                ctx.all(Bill.class).size(),
                "payment",
                ctx.all(Payment.class).size(),
                "request",
                ctx.all(ApprovalRequest.class).size(),
                "shift",
                ctx.all(CashierShift.class).size(),
                "order",
                ctx.all(Order.class).size());
    }

    /** Khóa idempotency của người dùng hiện tại, để client biết lệnh nào đã xử lý. */
    private Map<String, Object> processedKeys(SnapshotContext ctx) {
        Map<String, Object> keys = new LinkedHashMap<>();
        ctx.all(Payment.class).stream()
                .filter(p -> id(ctx.user).equals(p.getCreatedBy()) && p.getIdempotencyKey() != null)
                .forEach(p -> keys.put(p.getIdempotencyKey(), id(p)));
        return keys;
    }
}
