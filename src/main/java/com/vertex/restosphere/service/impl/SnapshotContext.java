package com.vertex.restosphere.service.impl;

import com.vertex.restosphere.entity.AuditLog;
import com.vertex.restosphere.entity.Bill;
import com.vertex.restosphere.entity.Notification;
import com.vertex.restosphere.entity.NotificationRead;
import com.vertex.restosphere.entity.Order;
import com.vertex.restosphere.entity.OrderItem;
import com.vertex.restosphere.entity.RestaurantSettings;
import com.vertex.restosphere.entity.RestaurantTable;
import com.vertex.restosphere.entity.ShiftAssignment;
import com.vertex.restosphere.entity.TableSession;
import com.vertex.restosphere.entity.User;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.service.WorkspaceStore;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Bộ nhớ đệm cho một lần dựng snapshot: mỗi bảng chỉ đọc một lần và được lập chỉ mục, thay vì quét
 * lại cả bảng cho từng dòng.
 */
final class SnapshotContext {

    final RestaurantSettings settings;
    final User user;
    final ZoneId zone;
    final Instant now = Instant.now();

    private final WorkspaceStore db;
    private final Map<Object, Object> memo = new HashMap<>();

    SnapshotContext(WorkspaceStore db, RestaurantSettings settings, User user) {
        this.db = db;
        this.settings = settings;
        this.user = user;
        this.zone = ZoneId.of(settings.getTimezone());
    }

    static <T, K> Map<K, List<T>> group(List<T> list, Function<T, K> key) {
        Map<K, List<T>> result = new HashMap<>();
        for (var x : list) {
            var k = key.apply(x);
            if (k != null) {
                result.computeIfAbsent(k, z -> new ArrayList<>()).add(x);
            }
        }
        return result;
    }

    static <T, K> Map<K, T> first(List<T> list, Function<T, K> key) {
        Map<K, T> result = new HashMap<>();
        for (var x : list) {
            result.putIfAbsent(key.apply(x), x);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private <T> T memo(Object key, Supplier<T> loader) {
        var value = (T) memo.get(key);
        if (value == null) {
            value = loader.get();
            memo.put(key, value);
        }
        return value;
    }

    <T> List<T> all(Class<T> type) {
        return memo(type, () -> db.all(type));
    }

    List<AuditLog> audit(UUID target) {
        return memo("audit", () -> group(all(AuditLog.class), AuditLog::getTargetId))
                .getOrDefault(target, List.of());
    }

    List<OrderItem> itemsOf(Order o) {
        return memo("itemsByOrder", () -> group(all(OrderItem.class), i -> i.getOrder().getId()))
                .getOrDefault(o.getId(), List.of());
    }

    Bill bill(TableSession session) {
        var bill =
                memo("billBySession", () -> first(all(Bill.class), b -> b.getSession().getId()))
                        .get(session.getId());
        if (bill == null) {
            throw ApiException.notFound("Bill");
        }
        return bill;
    }

    TableSession openSession(RestaurantTable table) {
        return memo(
                        "openSession",
                        () ->
                                first(
                                        all(TableSession.class).stream()
                                                .filter(
                                                        s ->
                                                                s.getStatus()
                                                                        != TableSession.Status
                                                                                .CLOSED)
                                                .toList(),
                                        s -> s.getTable().getId()))
                .get(table.getId());
    }

    List<ShiftAssignment> assignmentsOf(User u) {
        return memo(
                        "assignByUser",
                        () -> group(all(ShiftAssignment.class), a -> a.getUser().getId()))
                .getOrDefault(u.getId(), List.of());
    }

    List<NotificationRead> readsOf(Notification n) {
        return memo(
                        "reads",
                        () -> group(all(NotificationRead.class), r -> r.getNotification().getId()))
                .getOrDefault(n.getId(), List.of());
    }

    boolean mine(User owner) {
        return owner != null && owner.getId().equals(user.getId());
    }
}
