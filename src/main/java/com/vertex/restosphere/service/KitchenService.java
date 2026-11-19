package com.vertex.restosphere.service;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.dto.response.KitchenOrderItemResponse;
import com.vertex.restosphere.dto.response.KitchenOrderResponse;
import com.vertex.restosphere.entity.AuditLog;
import com.vertex.restosphere.entity.Order;
import com.vertex.restosphere.entity.OrderItem;
import com.vertex.restosphere.repository.AuditLogRepository;
import com.vertex.restosphere.repository.OrderItemRepository;
import com.vertex.restosphere.repository.OrderRepository;
import com.vertex.restosphere.security.CurrentUserService;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Phieu bep (REST). Chi doc; lenh start/complete/reject van o ServiceWorkflow.kitchen. */
@Service
@RequiredArgsConstructor
public class KitchenService {

    private static final List<String> HISTORY_ACTIONS = List.of("start", "complete", "reject");

    private final CurrentUserService currentUser;
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final AuditLogRepository auditLogs;

    @Transactional(readOnly = true)
    public List<KitchenOrderResponse> listOrders(UUID actorId, OrderItem.Status status) {
        currentUser.require(actorId, Role.ADMIN, Role.KITCHEN);
        List<Order> found = orders.findByStatusNotOrderByCreatedAtAsc(Order.Status.DRAFT);
        if (found.isEmpty()) {
            return List.of();
        }
        // Mot query lay mon cua moi order, mot query lay lich su cua moi mon (khong N+1).
        Map<UUID, List<OrderItem>> itemsByOrder = orderItems
                .findByOrderIdIn(found.stream().map(Order::getId).toList()).stream()
                .filter(i -> status == null || i.getStatus() == status)
                .sorted(Comparator.comparing(OrderItem::getCreatedAt))
                .collect(Collectors.groupingBy(i -> i.getOrder().getId()));
        List<UUID> itemIds = itemsByOrder.values().stream().flatMap(List::stream).map(OrderItem::getId).toList();
        Map<UUID, List<AuditLog>> logsByItem = itemIds.isEmpty() ? Map.of()
                : auditLogs.findByTargetTypeAndTargetIdInAndActionInOrderByCreatedAtAsc("ORDER_ITEM", itemIds,
                        HISTORY_ACTIONS).stream().collect(Collectors.groupingBy(AuditLog::getTargetId));

        return found.stream()
                .filter(o -> itemsByOrder.containsKey(o.getId()))
                .map(o -> toResponse(o, itemsByOrder.get(o.getId()), logsByItem))
                .toList();
    }

    private KitchenOrderResponse toResponse(Order o, List<OrderItem> items, Map<UUID, List<AuditLog>> logs) {
        var table = o.getSession().getTable();
        var priority = o.getPriorityAt() == null ? null
                : new KitchenOrderResponse.Priority(o.getPriorityAt(), o.getPriorityBy(), o.getPriorityReason());
        return new KitchenOrderResponse(o.getId(), "O-" + o.getId(), o.getSession().getId(), table.getCode(),
                table.getArea().getName(), o.getSession().getGuestCount(), o.getWaiter().getId(), o.getRoundNumber(),
                o.getCreatedAt(), priority,
                items.stream().map(i -> toResponse(i, logs.getOrDefault(i.getId(), List.of()))).toList());
    }

    private KitchenOrderItemResponse toResponse(OrderItem i, List<AuditLog> logs) {
        UUID chef = i.getChef() == null ? null : i.getChef().getId();
        // Entity khong luu thoi diem tu choi: lay tu audit "reject" moi nhat (logs da sap tang dan).
        AuditLog reject = logs.stream().filter(a -> "reject".equals(a.getAction()))
                .reduce((first, second) -> second).orElse(null);
        return new KitchenOrderItemResponse(i.getId(), i.getOrder().getId(), i.getMenuItem().getId(),
                i.getItemName(), i.getQuantity(), i.getNote() == null ? "" : i.getNote(), i.getStatus(),
                i.getVersion(), i.getCreatedAt(), i.getStartedAt(), chef, i.getReadyAt(), chef, i.getServedAt(),
                reject == null ? null : reject.getCreatedAt(),
                reject == null || reject.getActor() == null ? null : reject.getActor().getId(),
                i.getRejectionReason(),
                logs.stream().map(a -> new KitchenOrderItemResponse.History(a.getCreatedAt(), switch (a.getAction()) {
                    case "start" -> OrderItem.Status.PREPARING;
                    case "complete" -> OrderItem.Status.READY;
                    default -> OrderItem.Status.REJECTED;
                }, a.getActor() == null ? null : a.getActor().getId(), a.getDescription())).toList());
    }
}
