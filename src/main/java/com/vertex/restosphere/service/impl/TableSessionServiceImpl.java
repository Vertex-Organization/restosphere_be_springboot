package com.vertex.restosphere.service.impl;

import com.vertex.restosphere.common.enums.PriceUnit;
import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.common.enums.TableStatus;
import com.vertex.restosphere.dto.request.SplitItemRequest;
import com.vertex.restosphere.dto.response.SessionChangeResponse;
import com.vertex.restosphere.entity.ApprovalRequest;
import com.vertex.restosphere.entity.Bill;
import com.vertex.restosphere.entity.Employee;
import com.vertex.restosphere.entity.Order;
import com.vertex.restosphere.entity.OrderItem;
import com.vertex.restosphere.entity.Payment;
import com.vertex.restosphere.entity.TableSession;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.repository.ApprovalRequestRepository;
import com.vertex.restosphere.repository.OrderItemRepository;
import com.vertex.restosphere.repository.OrderRepository;
import com.vertex.restosphere.repository.PaymentRepository;
import com.vertex.restosphere.repository.RestaurantTableRepository;
import com.vertex.restosphere.repository.TableSessionRepository;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.BillingWorkflow;
import com.vertex.restosphere.service.RestaurantLock;
import com.vertex.restosphere.service.TableSessionService;
import com.vertex.restosphere.service.WorkspaceCommit;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class TableSessionServiceImpl implements TableSessionService {

    private static final Role[] ALLOWED = {
        Role.ADMIN, Role.MANAGER, Role.RECEPTIONIST, Role.WAITER, Role.CASHIER
    };

    private final CurrentUserService currentUser;
    private final RestaurantLock restaurantLock;
    private final BillingWorkflow billing;
    private final WorkspaceCommit writes;
    private final TableSessionRepository sessions;
    private final RestaurantTableRepository tables;
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final PaymentRepository payments;
    private final ApprovalRequestRepository approvals;

    @Override
    public SessionChangeResponse merge(UUID actorId, UUID targetSessionId, UUID sourceSessionId) {
        Employee user = currentUser.require(actorId, ALLOWED);
        restaurantLock.acquire();
        if (targetSessionId.equals(sourceSessionId)) {
            throw ApiException.badRequest("Hãy chọn một bàn khác để ghép.");
        }
        TableSession target = find(targetSessionId);
        TableSession source = find(sourceSessionId);
        requireOwnSession(user, target);
        requireOwnSession(user, source);
        Bill keep = target.getBill();
        Bill voided = source.getBill();
        if (keep.getId().equals(voided.getId())) {
            throw ApiException.conflict("Hai bàn này đã dùng chung một hóa đơn.");
        }
        requireMergeable(keep);
        requireMergeable(voided);

        List<TableSession> moving = List.copyOf(voided.getSessions());
        moving.forEach(s -> requireOwnSession(user, s));
        keep.getSessions().forEach(s -> requireOwnSession(user, s));
        moving.forEach(s -> s.setBill(keep));
        keep.getSessions().addAll(moving);
        voided.getSessions().clear();
        voided.setStatus(Bill.Status.VOIDED);
        billing.recalculate(keep);
        writes.commit(user, "merge-sessions");
        return new SessionChangeResponse(target.getId(), keep.getId(), keep.getCode());
    }

    @Override
    public SessionChangeResponse split(
            UUID actorId,
            UUID sessionId,
            UUID targetTableId,
            List<SplitItemRequest> items,
            int guestCount) {
        Employee user = currentUser.require(actorId, ALLOWED);
        restaurantLock.acquire();
        TableSession from = find(sessionId);
        requireOwnSession(user, from);
        Bill old = from.getBill();
        requireMergeable(old);
        var table =
                tables.findById(targetTableId).orElseThrow(() -> ApiException.notFound("Table"));
        if (table.getId().equals(from.getTable().getId())) {
            throw ApiException.badRequest("Hãy chọn một bàn trống khác để tách.");
        }
        if (table.getStatus() != TableStatus.AVAILABLE
                || sessions.existsByTableIdAndStatusNot(
                        table.getId(), TableSession.Status.CLOSED)) {
            throw ApiException.conflict("Bàn được chọn không trống.");
        }
        List<SplitLine> lines = movableItems(from, items);
        if (guestCount < 1 || guestCount >= from.getGuestCount()) {
            throw ApiException.badRequest(
                    "guestCount",
                    "Số khách chuyển sang bàn mới phải từ 1 và nhỏ hơn số khách hiện tại của bàn ("
                            + from.getGuestCount()
                            + ").");
        }

        Bill bill = billing.open();
        TableSession session = new TableSession();
        session.setBill(bill);
        bill.getSessions().add(session);
        session.setTable(table);
        session.setWaiter(from.getWaiter());
        session.setAcceptedAt(Instant.now());
        session.setGuestCount(guestCount);
        from.setGuestCount(from.getGuestCount() - guestCount);
        session.setStartedAt(Instant.now());
        sessions.save(session);

        Order order = new Order();
        order.setSession(session);
        order.setWaiter(from.getWaiter());
        order.setStatus(Order.Status.SUBMITTED);
        order.setRoundNumber(1);
        order.setSubmittedAt(Instant.now());
        orders.save(order);
        lines.forEach(line -> moveLine(line, order));

        table.setStatus(TableStatus.OCCUPIED);
        billing.recalculate(old);
        billing.recalculate(bill);
        writes.commit(user, "split-session");
        return new SessionChangeResponse(session.getId(), bill.getId(), bill.getCode());
    }

    // A waiter only touches the tables they serve; other roles are not limited.
    private static void requireOwnSession(Employee user, TableSession session) {
        if (user.getRole() == Role.WAITER
                && (session.getWaiter() == null
                        || !session.getWaiter().getId().equals(user.getId()))) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN, "Bạn chỉ được ghép hoặc tách bàn do mình phụ trách.");
        }
    }

    private TableSession find(UUID id) {
        TableSession s = sessions.findById(id).orElseThrow(() -> ApiException.notFound("Session"));
        if (s.getStatus() != TableSession.Status.OPEN || s.getGuestLeftAt() != null) {
            throw ApiException.conflict(
                    "Chỉ ghép hoặc tách được bàn đang phục vụ và chưa yêu cầu thanh toán.");
        }
        return s;
    }

    private record SplitLine(OrderItem item, BigDecimal quantity) {}

    private List<SplitLine> movableItems(TableSession from, List<SplitItemRequest> items) {
        var ids = items.stream().map(SplitItemRequest::itemId).collect(Collectors.toSet());
        if (ids.size() != items.size()) {
            throw ApiException.badRequest("items", "Mỗi món chỉ được chọn một lần.");
        }
        var owned =
                orderItems.findByOrderSessionId(from.getId()).stream()
                        .collect(Collectors.toMap(OrderItem::getId, Function.identity()));
        return items.stream().map(req -> toLine(owned.get(req.itemId()), req)).toList();
    }

    private SplitLine toLine(OrderItem item, SplitItemRequest req) {
        if (item == null) {
            throw ApiException.badRequest("items", "Có món không thuộc bàn đang tách.");
        }
        if (OrderItem.NOT_BILLABLE.contains(item.getStatus())) {
            throw ApiException.badRequest("items", "Chỉ tách được món đã gửi bếp và chưa bị hủy.");
        }
        if (req.quantity() == null) {
            return new SplitLine(item, item.getQuantity());
        }
        BigDecimal quantity = req.quantity();
        if (quantity.signum() <= 0 || quantity.compareTo(item.getQuantity()) > 0) {
            throw ApiException.badRequest(
                    "items", "Số lượng tách phải lớn hơn 0 và không vượt quá số lượng của món.");
        }
        boolean whole = quantity.compareTo(item.getQuantity()) == 0;
        boolean fractional = quantity.stripTrailingZeros().scale() > 0;
        if (!whole && fractional) {
            throw ApiException.badRequest(
                    "items", "Chỉ được tách số lượng nguyên với món không tính theo kg.");
        }
        if (!whole && item.getMenuItem().getPriceUnit() == PriceUnit.KG) {
            throw ApiException.badRequest(
                    "items", "Món tính theo kg chỉ được chuyển cả dòng.");
        }
        return new SplitLine(item, quantity);
    }

    private void moveLine(SplitLine line, Order target) {
        OrderItem item = line.item();
        if (line.quantity().compareTo(item.getQuantity()) == 0) {
            item.setOrder(target);
            return;
        }
        item.setQuantity(item.getQuantity().subtract(line.quantity()));
        orderItems.save(copyOf(item, target, line.quantity()));
    }

    private static OrderItem copyOf(OrderItem item, Order target, BigDecimal quantity) {
        var copy = new OrderItem();
        copy.setOrder(target);
        copy.setMenuItem(item.getMenuItem());
        copy.setItemName(item.getItemName());
        copy.setUnit(item.getUnit());
        copy.setUnitPrice(item.getUnitPrice());
        copy.setQuantity(quantity);
        copy.setStatus(item.getStatus());
        copy.setChef(item.getChef());
        copy.setStartedAt(item.getStartedAt());
        copy.setReadyAt(item.getReadyAt());
        copy.setServedAt(item.getServedAt());
        copy.setNote(item.getNote());
        copy.setRejectionReason(item.getRejectionReason());
        return copy;
    }

    // Shared by merge and split: money already allocated to a bill cannot be re-shared safely.
    private void requireMergeable(Bill bill) {
        if (bill.getStatus() != Bill.Status.OPEN) {
            throw ApiException.conflict("Hóa đơn " + bill.getCode() + " đã đóng.");
        }
        if (bill.getDiscountValue() != null) {
            throw ApiException.conflict(
                    "Hóa đơn " + bill.getCode() + " đã áp dụng giảm giá nên không ghép/tách được.");
        }
        if (approvals.existsByTypeAndTargetIdAndStatus(
                ApprovalRequest.Type.DISCOUNT, bill.getId(), ApprovalRequest.Status.PENDING)) {
            throw ApiException.conflict(
                    "Hóa đơn " + bill.getCode() + " đang có yêu cầu giảm giá chờ duyệt.");
        }
        if (payments.existsByBillIdAndStatus(bill.getId(), Payment.Status.COMPLETED)) {
            throw ApiException.conflict(
                    "Hóa đơn " + bill.getCode() + " đã có thanh toán nên không ghép/tách được.");
        }
        if (payments.existsByBillIdAndStatus(bill.getId(), Payment.Status.PENDING)) {
            throw ApiException.conflict(
                    "Hóa đơn " + bill.getCode() + " đang có mã QR payOS chờ thanh toán.");
        }
        for (TableSession s : bill.getSessions()) {
            if (s.getReservation() != null
                    && payments.existsByReservationIdAndStatus(
                            s.getReservation().getId(), Payment.Status.COMPLETED)) {
                throw ApiException.conflict(
                        "Bàn " + s.getTable().getCode() + " có tiền cọc nên không ghép/tách được.");
            }
        }
    }
}
