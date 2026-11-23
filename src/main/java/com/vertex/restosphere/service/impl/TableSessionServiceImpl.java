package com.vertex.restosphere.service.impl;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.common.enums.TableStatus;
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
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
        Bill keep = target.getBill();
        Bill voided = source.getBill();
        if (keep.getId().equals(voided.getId())) {
            throw ApiException.conflict("Hai bàn này đã dùng chung một hóa đơn.");
        }
        requireMergeable(keep);
        requireMergeable(voided);

        List<TableSession> moving = List.copyOf(voided.getSessions());
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
            UUID actorId, UUID sessionId, UUID targetTableId, List<UUID> itemIds) {
        Employee user = currentUser.require(actorId, ALLOWED);
        restaurantLock.acquire();
        TableSession from = find(sessionId);
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
        List<OrderItem> moved = movableItems(from, itemIds);

        Bill bill = billing.open();
        TableSession session = new TableSession();
        session.setBill(bill);
        bill.getSessions().add(session);
        session.setTable(table);
        session.setWaiter(from.getWaiter());
        session.setAcceptedAt(Instant.now());
        // TODO(confirm): guest split is not specified; the new table starts with 1 guest.
        session.setGuestCount(1);
        session.setStartedAt(Instant.now());
        sessions.save(session);

        Order order = new Order();
        order.setSession(session);
        order.setWaiter(from.getWaiter());
        order.setStatus(Order.Status.SUBMITTED);
        order.setRoundNumber(1);
        order.setSubmittedAt(Instant.now());
        orders.save(order);
        moved.forEach(i -> i.setOrder(order));

        table.setStatus(TableStatus.OCCUPIED);
        billing.recalculate(old);
        billing.recalculate(bill);
        writes.commit(user, "split-session");
        return new SessionChangeResponse(session.getId(), bill.getId(), bill.getCode());
    }

    private TableSession find(UUID id) {
        TableSession s = sessions.findById(id).orElseThrow(() -> ApiException.notFound("Session"));
        if (s.getStatus() != TableSession.Status.OPEN || s.getGuestLeftAt() != null) {
            throw ApiException.conflict(
                    "Chỉ ghép hoặc tách được bàn đang phục vụ và chưa yêu cầu thanh toán.");
        }
        return s;
    }

    private List<OrderItem> movableItems(TableSession from, List<UUID> itemIds) {
        var unique = new HashSet<>(itemIds);
        var owned = orderItems.findByOrderSessionId(from.getId());
        var chosen = owned.stream().filter(i -> unique.contains(i.getId())).toList();
        if (chosen.size() != unique.size()) {
            throw ApiException.badRequest("itemIds", "Có món không thuộc bàn đang tách.");
        }
        if (chosen.stream().anyMatch(i -> OrderItem.NOT_BILLABLE.contains(i.getStatus()))) {
            throw ApiException.badRequest(
                    "itemIds", "Chỉ tách được món đã gửi bếp và chưa bị hủy.");
        }
        return chosen;
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
