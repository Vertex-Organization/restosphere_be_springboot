package com.vertex.restosphere.service;

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

/**
 * Giam sat don cho trang Manager (chi doc); thay phan "orders" cua snapshot cu (snapshot van giu).
 * Du lieu = 3 query: don (kem ban/khu/waiter), mon (kem chef), hoa don; khong phu thuoc so don.
 */
@Service
@RequiredArgsConstructor
public class ManagerOrderService {

    private final CurrentUserService currentUser;
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final ApprovalQueryRepository approvalQueries;
    private final RestaurantSettingsRepository settings;

    @Transactional(readOnly = true)
    public List<ManagerOrderResponse> list(UUID actorId, LocalDate date) {
        currentUser.require(actorId, Role.ADMIN, Role.MANAGER);
        ZoneId zone = settings.findBySingletonKey(1).map(RestaurantSettings::getTimezone).map(ZoneId::of)
                .orElse(ZoneId.of("Asia/Ho_Chi_Minh"));
        LocalDate day = date == null ? LocalDate.now(zone) : date;
        // Trong ngay HOAC phien chua dong: ban ngoi qua nua dem van hien du cac luot.
        List<Order> list = orders.findForManager(Order.Status.DRAFT, TableSession.Status.CLOSED,
                day.atStartOfDay(zone).toInstant(), day.plusDays(1).atStartOfDay(zone).toInstant());
        if (list.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<OrderItem>> itemsByOrder = orderItems.findByOrderIdIn(list.stream().map(Order::getId).toList())
                .stream().collect(Collectors.groupingBy(i -> i.getOrder().getId()));
        Set<UUID> sessionIds = list.stream().map(o -> o.getSession().getId()).collect(Collectors.toSet());
        // Chi doc hoa don co san, khong tao moi (snapshot cu goi billing.bill se tao neu thieu).
        Set<UUID> paidSessions = approvalQueries.findBillsBySessionIdIn(sessionIds).stream()
                .filter(b -> b.getStatus() == Bill.Status.PAID).map(b -> b.getSession().getId())
                .collect(Collectors.toSet());
        Comparator<OrderItem> byCreated = Comparator.comparing(OrderItem::getCreatedAt)
                .thenComparing(OrderItem::getId);
        return list.stream().map(o -> ManagerOrderResponse.from(o,
                itemsByOrder.getOrDefault(o.getId(), List.of()).stream().sorted(byCreated)
                        .map(ManagerOrderItemResponse::from).toList(),
                paidSessions.contains(o.getSession().getId()))).toList();
    }
}
