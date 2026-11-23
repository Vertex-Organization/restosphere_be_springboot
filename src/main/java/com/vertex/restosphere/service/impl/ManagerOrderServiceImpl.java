package com.vertex.restosphere.service.impl;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.dto.response.ManagerOrderItemResponse;
import com.vertex.restosphere.dto.response.ManagerOrderResponse;
import com.vertex.restosphere.entity.Bill;
import com.vertex.restosphere.entity.Order;
import com.vertex.restosphere.entity.OrderItem;
import com.vertex.restosphere.entity.RestaurantSettings;
import com.vertex.restosphere.entity.TableSession;
import com.vertex.restosphere.repository.ApprovalQueryRepository;
import com.vertex.restosphere.repository.OrderItemRepository;
import com.vertex.restosphere.repository.OrderRepository;
import com.vertex.restosphere.repository.RestaurantSettingsRepository;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.ManagerOrderService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagerOrderServiceImpl implements ManagerOrderService {

    private final CurrentUserService currentUser;
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final ApprovalQueryRepository approvalQueries;
    private final RestaurantSettingsRepository settings;

    @Transactional(readOnly = true)
    @Override
    public List<ManagerOrderResponse> list(UUID actorId, LocalDate date) {
        currentUser.require(actorId, Role.ADMIN, Role.MANAGER);
        ZoneId zone =
                settings.findBySingletonKey(1)
                        .map(RestaurantSettings::getTimezone)
                        .map(ZoneId::of)
                        .orElse(ZoneId.of("Asia/Ho_Chi_Minh"));
        LocalDate day = date == null ? LocalDate.now(zone) : date;
        List<Order> list =
                orders.findForManager(
                        Order.Status.SUBMITTED,
                        TableSession.Status.CLOSED,
                        day.atStartOfDay(zone).toInstant(),
                        day.plusDays(1).atStartOfDay(zone).toInstant());
        if (list.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<OrderItem>> itemsByOrder =
                orderItems.findByOrderIdIn(list.stream().map(Order::getId).toList()).stream()
                        .collect(Collectors.groupingBy(i -> i.getOrder().getId()));
        Set<UUID> sessionIds =
                list.stream().map(o -> o.getSession().getId()).collect(Collectors.toSet());
        Set<UUID> paidSessions =
                approvalQueries.findSessionsWithBillByIdIn(sessionIds).stream()
                        .filter(s -> s.getBill().getStatus() == Bill.Status.PAID)
                        .map(TableSession::getId)
                        .collect(Collectors.toSet());
        Comparator<OrderItem> byCreated =
                Comparator.comparing(OrderItem::getCreatedAt).thenComparing(OrderItem::getId);
        return list.stream()
                .map(
                        o ->
                                ManagerOrderResponse.from(
                                        o,
                                        itemsByOrder.getOrDefault(o.getId(), List.of()).stream()
                                                .sorted(byCreated)
                                                .map(ManagerOrderItemResponse::from)
                                                .toList(),
                                        paidSessions.contains(o.getSession().getId())))
                .toList();
    }
}
