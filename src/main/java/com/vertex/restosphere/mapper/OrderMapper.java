package com.vertex.restosphere.mapper;

import static com.vertex.restosphere.common.util.Values.code;
import static com.vertex.restosphere.common.util.Values.id;
import static com.vertex.restosphere.common.util.Values.map;
import static com.vertex.restosphere.common.util.Values.str;

import com.vertex.restosphere.entity.AuditLog;
import com.vertex.restosphere.entity.Bill;
import com.vertex.restosphere.entity.Order;
import com.vertex.restosphere.entity.OrderDraft;
import com.vertex.restosphere.entity.OrderItem;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Đơn gọi món, món và nháp dạng JSON cho các workspace. */
@Component
public class OrderMapper {

    public Map<String, Object> toMap(
            Order o,
            Bill bill,
            List<OrderItem> items,
            java.util.function.Function<OrderItem, List<AuditLog>> historyOf) {
        var table = o.getSession().getTable();
        return map(
                "id",
                id(o),
                "code",
                code("O", o),
                "sessionId",
                id(o.getSession()),
                "tableCode",
                table.getCode(),
                "area",
                table.getArea().getName(),
                "guests",
                o.getSession().getGuestCount(),
                "waiterId",
                id(o.getWaiter()),
                "employeeId",
                id(o.getWaiter()),
                "sequence",
                o.getRoundNumber(),
                "round",
                o.getRoundNumber(),
                "submittedAt",
                o.getCreatedAt(),
                "at",
                o.getCreatedAt(),
                "paid",
                bill.getStatus() == Bill.Status.PAID,
                "priority",
                o.getPriorityAt() == null
                        ? null
                        : map(
                                "at",
                                o.getPriorityAt(),
                                "by",
                                id(o.getPriorityBy()),
                                "reason",
                                o.getPriorityReason()),
                "items",
                items.stream().map(i -> toMap(i, historyOf.apply(i))).toList());
    }

    public Map<String, Object> toMap(OrderItem i, List<AuditLog> history) {
        return map(
                "id",
                id(i),
                "orderId",
                id(i.getOrder()),
                "menuItemId",
                id(i.getMenuItem()),
                "menuId",
                id(i.getMenuItem()),
                "name",
                i.getItemName(),
                "unitPrice",
                i.getUnitPrice(),
                "quantity",
                i.getQuantity(),
                "note",
                str(i.getNote()),
                "status",
                i.getStatus(),
                "version",
                i.getVersion(),
                "submittedAt",
                i.getCreatedAt(),
                "startedAt",
                i.getStartedAt(),
                "startedBy",
                id(i.getChef()),
                "chefId",
                id(i.getChef()),
                "readyAt",
                i.getReadyAt(),
                "readyBy",
                id(i.getChef()),
                "servedAt",
                i.getServedAt(),
                "rejectionReason",
                i.getRejectionReason(),
                "cancellationReason",
                i.getRejectionReason(),
                "history",
                history.stream()
                        .map(
                                a ->
                                        map(
                                                "at",
                                                a.getCreatedAt(),
                                                "by",
                                                id(a.getActor()),
                                                "status",
                                                historyStatus(a, i),
                                                "note",
                                                a.getDescription()))
                        .toList());
    }

    public Map<String, Object> toMap(OrderDraft d) {
        return map(
                "id",
                id(d),
                "sessionId",
                id(d.getSession()),
                "waiterId",
                id(d.getWaiter()),
                "menuItemId",
                id(d.getMenuItem()),
                "quantity",
                d.getQuantity(),
                "note",
                str(d.getNote()));
    }

    private static String historyStatus(AuditLog a, OrderItem i) {
        return switch (a.getAction()) {
            case "start" -> "PREPARING";
            case "complete" -> "READY";
            case "reject" -> "REJECTED";
            default -> i.getStatus().name();
        };
    }
}
