package com.vertex.restosphere.service.impl;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.dto.request.KitchenItemRef;
import com.vertex.restosphere.dto.response.KitchenOrderItemResponse;
import com.vertex.restosphere.dto.response.KitchenOrderResponse;
import com.vertex.restosphere.entity.AuditLog;
import com.vertex.restosphere.entity.Employee;
import com.vertex.restosphere.entity.Order;
import com.vertex.restosphere.entity.OrderItem;
import com.vertex.restosphere.entity.RestaurantSettings;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.repository.AuditLogRepository;
import com.vertex.restosphere.repository.OrderItemRepository;
import com.vertex.restosphere.repository.OrderRepository;
import com.vertex.restosphere.repository.RestaurantSettingsRepository;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.BillingWorkflow;
import com.vertex.restosphere.service.KitchenService;
import com.vertex.restosphere.service.RestaurantLock;
import com.vertex.restosphere.service.ServiceWorkflow;
import com.vertex.restosphere.service.WorkspaceStore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KitchenServiceImpl implements KitchenService {
    private static final List<String> HISTORY_ACTIONS = List.of("start", "complete", "reject");

    private final CurrentUserService currentUser;
    private final RestaurantLock restaurantLock;
    private final WorkspaceStore store;
    private final ServiceWorkflow workflow;
    private final BillingWorkflow billing;
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final AuditLogRepository auditLogs;
    private final RestaurantSettingsRepository settings;

    @Transactional(readOnly = true)
    @Override
    public List<KitchenOrderResponse> listOrders(UUID actorId, OrderItem.Status status) {
        currentUser.require(actorId, Role.ADMIN, Role.KITCHEN);
        List<Order> found = orders.findAllWithTable();
        if (found.isEmpty()) {
            return List.of();
        }

        Map<UUID, List<OrderItem>> itemsByOrder =
                orderItems.findByOrderIdIn(found.stream().map(Order::getId).toList()).stream()
                        .filter(i -> status == null || i.getStatus() == status)
                        .sorted(Comparator.comparing(OrderItem::getCreatedAt))
                        .collect(Collectors.groupingBy(i -> i.getOrder().getId()));
        Map<UUID, List<AuditLog>> logsByItem =
                history(
                        itemsByOrder.values().stream()
                                .flatMap(List::stream)
                                .map(OrderItem::getId)
                                .toList());

        return found.stream()
                .filter(o -> itemsByOrder.containsKey(o.getId()))
                .map(o -> toResponse(o, itemsByOrder.get(o.getId()), logsByItem))
                .toList();
    }

    @Transactional
    @Override
    public List<KitchenOrderItemResponse> start(UUID actorId, List<KitchenItemRef> refs) {
        return transition(actorId, "start", refs, null);
    }

    @Transactional
    @Override
    public List<KitchenOrderItemResponse> complete(UUID actorId, List<KitchenItemRef> refs) {
        return transition(actorId, "complete", refs, null);
    }

    @Transactional
    @Override
    public List<KitchenOrderItemResponse> reject(
            UUID actorId, UUID itemId, long version, String reason) {
        return transition(
                actorId,
                "reject",
                List.of(new KitchenItemRef(itemId.toString(), version)),
                reason.trim());
    }

    private List<KitchenOrderItemResponse> transition(
            UUID actorId, String type, List<KitchenItemRef> refs, String reason) {
        Employee user = currentUser.require(actorId, Role.ADMIN, Role.KITCHEN);
        if (refs == null || refs.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Select at least one item");
        }
        restaurantLock.acquire();
        OrderItem.Status expected =
                type.equals("complete") ? OrderItem.Status.PREPARING : OrderItem.Status.SUBMITTED;

        Set<UUID> seen = new HashSet<>();
        List<OrderItem> items = new ArrayList<>();
        for (KitchenItemRef ref : refs) {
            OrderItem item = store.get(OrderItem.class, ref.id());
            if (!seen.add(item.getId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Duplicate item");
            }
            if (ref.version() != item.getVersion()) {
                throw ApiException.conflict("Item changed; refresh");
            }
            if (item.getStatus() != expected) {
                throw ApiException.conflict("Invalid item transition");
            }
            items.add(item);
        }
        for (OrderItem item : items) {
            String session = item.getOrder().getSession().getId().toString();
            switch (type) {
                case "start" -> {
                    item.setStatus(OrderItem.Status.PREPARING);
                    item.setChef(user);
                    item.setStartedAt(Instant.now());
                }
                case "complete" -> {
                    item.setStatus(OrderItem.Status.READY);
                    item.setReadyAt(Instant.now());
                    workflow.notify(
                            Role.WAITER, "READY", "Dish ready", item.getItemName(), session);
                }
                default -> {
                    item.setStatus(OrderItem.Status.REJECTED);
                    item.setRejectionReason(reason);
                    billing.recalculate(billing.bill(item.getOrder().getSession()));
                    workflow.notify(Role.WAITER, "ITEM_REJECTED", "Dish rejected", reason, session);
                }
            }
            workflow.audit(user, type, item.getId(), "ORDER_ITEM", type);
        }

        RestaurantSettings s = settings.findBySingletonKey(1).orElseGet(() -> store.lock());
        s.setRevision(s.getRevision() + 1);
        orderItems.flush();
        Map<UUID, List<AuditLog>> logs = history(items.stream().map(OrderItem::getId).toList());
        return items.stream()
                .map(i -> toResponse(i, logs.getOrDefault(i.getId(), List.of())))
                .toList();
    }

    private Map<UUID, List<AuditLog>> history(List<UUID> itemIds) {
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        return auditLogs
                .findByTargetTypeAndTargetIdInAndActionInOrderByCreatedAtAsc(
                        "ORDER_ITEM", itemIds, HISTORY_ACTIONS)
                .stream()
                .collect(Collectors.groupingBy(AuditLog::getTargetId));
    }

    private KitchenOrderResponse toResponse(
            Order o, List<OrderItem> items, Map<UUID, List<AuditLog>> logs) {
        var table = o.getSession().getTable();
        var priority =
                o.getPriorityAt() == null
                        ? null
                        : new KitchenOrderResponse.Priority(
                                o.getPriorityAt(),
                                o.getPriorityBy() == null
                                        ? null
                                        : o.getPriorityBy().getId().toString(),
                                o.getPriorityReason());
        return new KitchenOrderResponse(
                o.getId(),
                "O-" + o.getId(),
                o.getSession().getId(),
                table.getCode(),
                table.getArea().getName(),
                o.getSession().getGuestCount(),
                o.getWaiter().getId(),
                o.getRoundNumber(),
                o.getCreatedAt(),
                priority,
                items.stream()
                        .map(i -> toResponse(i, logs.getOrDefault(i.getId(), List.of())))
                        .toList());
    }

    private KitchenOrderItemResponse toResponse(OrderItem i, List<AuditLog> logs) {
        UUID chef = i.getChef() == null ? null : i.getChef().getId();

        AuditLog reject =
                logs.stream()
                        .filter(a -> "reject".equals(a.getAction()))
                        .reduce((first, second) -> second)
                        .orElse(null);
        return new KitchenOrderItemResponse(
                i.getId(),
                i.getOrder().getId(),
                i.getMenuItem().getId(),
                i.getItemName(),
                i.getQuantity(),
                i.getNote() == null ? "" : i.getNote(),
                i.getStatus(),
                i.getVersion(),
                i.getCreatedAt(),
                i.getStartedAt(),
                chef,
                i.getReadyAt(),
                chef,
                i.getServedAt(),
                reject == null ? null : reject.getCreatedAt(),
                reject == null || reject.getActor() == null ? null : reject.getActor().getId(),
                i.getRejectionReason(),
                logs.stream()
                        .map(
                                a ->
                                        new KitchenOrderItemResponse.History(
                                                a.getCreatedAt(),
                                                switch (a.getAction()) {
                                                    case "start" -> OrderItem.Status.PREPARING;
                                                    case "complete" -> OrderItem.Status.READY;
                                                    default -> OrderItem.Status.REJECTED;
                                                },
                                                a.getActor() == null ? null : a.getActor().getId(),
                                                a.getDescription()))
                        .toList());
    }
}
