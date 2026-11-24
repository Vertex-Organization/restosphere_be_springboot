package com.vertex.restosphere.service.impl;

import static com.vertex.restosphere.common.util.Values.id;
import static com.vertex.restosphere.common.util.Values.invalid;
import static com.vertex.restosphere.common.util.Values.number;
import static com.vertex.restosphere.common.util.Values.required;
import static com.vertex.restosphere.common.util.Values.text;
import static com.vertex.restosphere.common.util.Values.validateQuantity;

import com.vertex.restosphere.common.enums.MenuItemStatus;
import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.common.enums.TableStatus;
import com.vertex.restosphere.common.util.Values;
import com.vertex.restosphere.entity.*;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.repository.*;
import com.vertex.restosphere.service.BillingWorkflow;
import com.vertex.restosphere.service.ServiceWorkflow;
import com.vertex.restosphere.service.WaiterService;
import com.vertex.restosphere.service.WorkspaceStore;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WaiterServiceImpl implements WaiterService {

    private static final Set<OrderItem.Status> NOT_SERVED_YET =
            EnumSet.of(
                    OrderItem.Status.DRAFT,
                    OrderItem.Status.SUBMITTED,
                    OrderItem.Status.PREPARING,
                    OrderItem.Status.READY);
    private static final Set<OrderItem.Status> CANCELLABLE =
            EnumSet.of(
                    OrderItem.Status.SUBMITTED, OrderItem.Status.PREPARING, OrderItem.Status.READY);
    private static final int MAX_NOTE = 1000;

    private final WorkspaceStore db;
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final ReservationItemRepository reservationItems;
    private final ApprovalRequestRepository approvals;
    private final BillingWorkflow billing;
    private final ServiceWorkflow events;

    @Override
    public void handle(String type, Map<String, Object> command, Employee waiter) {
        UUID session = uuid(command, "sessionId");
        switch (type) {
            case "accept" -> accept(session, waiter);
            case "add-draft" -> addDraft(session, uuid(command, "menuItemId"), waiter);
            case "edit-draft" ->
                    editDraft(
                            session,
                            uuid(command, "draftId"),
                            number(command, "quantity"),
                            text(command, "note"),
                            waiter);
            case "remove-draft" -> removeDraft(session, uuid(command, "draftId"), waiter);
            case "submit" -> submit(session, waiter);
            case "serve" -> serve(session, uuid(command, "itemId"), waiter);
            case "request-cancel" ->
                    requestCancel(
                            session,
                            uuid(command, "itemId"),
                            required(command, "reason", MAX_NOTE),
                            waiter);
            case "request-priority" ->
                    requestPriority(
                            session,
                            uuid(command, "orderId"),
                            required(command, "reason", MAX_NOTE),
                            waiter);
            case "request-payment" -> requestPayment(session, waiter);
            case "withdraw-payment-request" -> withdrawPaymentRequest(session, waiter);
            case "guest-left" -> guestLeft(session, isConfirmed(command), waiter);
            case "clean" -> clean(session, isConfirmed(command), waiter);
            default -> throw invalid("Unsupported waiter command");
        }
    }

    @Override
    public void accept(UUID sessionId, Employee waiter) {
        TableSession session = db.get(TableSession.class, sessionId.toString());
        if (session.getStatus() != TableSession.Status.OPEN || session.getWaiter() != null) {
            throw ApiException.conflict("Session cannot be accepted");
        }
        Area area = waiter.getAssignedArea();
        if (area != null && !same(area, session.getTable().getArea())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Table is outside your assigned area");
        }
        session.setWaiter(waiter);
        session.setAcceptedAt(Instant.now());
        if (session.getReservation() != null) {
            for (ReservationItem preorder :
                    reservationItems.findByReservation(session.getReservation())) {
                addToCart(
                        session,
                        waiter,
                        preorder.getMenuItem(),
                        preorder.getQuantity(),
                        preorder.getNote());
            }
        }
    }

    @Override
    public void addDraft(UUID sessionId, UUID menuItemId, Employee waiter) {
        TableSession session = orderingSession(sessionId, waiter);
        MenuItem menu = db.get(MenuItem.class, menuItemId.toString());
        requireAvailable(menu);
        addToCart(session, waiter, menu, BigDecimal.ONE, "");
    }

    @Override
    public void editDraft(
            UUID sessionId, UUID draftId, BigDecimal quantity, String note, Employee waiter) {
        TableSession session = orderingSession(sessionId, waiter);
        OrderItem draft = ownDraft(session, waiter, draftId);
        String cleanNote = note == null ? "" : note.trim();
        validateQuantity(draft.getMenuItem(), quantity);
        if (cleanNote.length() > MAX_NOTE) throw invalid("Note too long");
        draft.setQuantity(quantity);
        draft.setNote(cleanNote);
    }

    @Override
    public void removeDraft(UUID sessionId, UUID draftId, Employee waiter) {
        TableSession session = orderingSession(sessionId, waiter);
        orderItems.delete(ownDraft(session, waiter, draftId));
    }

    @Override
    public void submit(UUID sessionId, Employee waiter) {
        TableSession session = orderingSession(sessionId, waiter);
        Order order =
                orders.findBySessionAndWaiterAndStatus(session, waiter, Order.Status.DRAFT)
                        .orElseThrow(() -> invalid("No draft items"));
        var cart = orderItems.findByOrderIdIn(List.of(order.getId()));
        if (cart.isEmpty()) throw invalid("No draft items");

        for (OrderItem item : cart) {
            MenuItem menu = item.getMenuItem();
            requireAvailable(menu);
            item.setItemName(menu.getName());
            item.setUnit(menu.getPriceUnit().getValue());
            item.setUnitPrice(menu.getPrice());
            item.setStatus(OrderItem.Status.SUBMITTED);
        }
        order.setRoundNumber(orders.findLastRoundNumber(session) + 1);
        order.setSubmittedAt(Instant.now());
        order.setStatus(Order.Status.SUBMITTED);
        billing.recalculate(billing.bill(session));
        events.notify(
                Role.KITCHEN, "NEW_ORDER", "New order", session.getTable().getCode(), id(order));
    }

    @Override
    public void serve(UUID sessionId, UUID itemId, Employee waiter) {
        TableSession session = ownSession(sessionId, waiter);
        OrderItem item = sessionItem(session, itemId);
        if (item.getStatus() != OrderItem.Status.READY)
            throw ApiException.conflict("Item is not ready");
        item.setStatus(OrderItem.Status.SERVED);
        item.setServedAt(Instant.now());
    }

    @Override
    public void requestCancel(UUID sessionId, UUID itemId, String reason, Employee waiter) {
        TableSession session = orderingSession(sessionId, waiter);
        OrderItem item = sessionItem(session, itemId);
        if (!CANCELLABLE.contains(item.getStatus()))
            throw ApiException.conflict("Item cannot be cancelled");
        if (approvals.existsByTypeAndTargetIdAndStatus(
                ApprovalRequest.Type.CANCEL_ITEM, item.getId(), ApprovalRequest.Status.PENDING)) {
            throw ApiException.conflict("A cancel request is already pending");
        }
        BigDecimal amount = Values.whole(item.getUnitPrice().multiply(item.getQuantity()));
        billing.request(
                ApprovalRequest.Type.CANCEL_ITEM,
                "ORDER_ITEM",
                item.getId(),
                amount,
                reason,
                waiter);
    }

    @Override
    public void requestPriority(UUID sessionId, UUID orderId, String reason, Employee waiter) {
        TableSession session = ownSession(sessionId, waiter);
        Order order = db.get(Order.class, orderId.toString());
        if (!same(order.getSession(), session)) throw invalid("Order belongs to another session");
        order.setPriorityAt(Instant.now());
        order.setPriorityBy(waiter);
        order.setPriorityReason(reason);
        events.notify(Role.KITCHEN, "PRIORITY", "Priority requested", reason, id(order));
    }

    @Override
    public void requestPayment(UUID sessionId, Employee waiter) {
        TableSession session = ownSession(sessionId, waiter);
        if (session.getStatus() != TableSession.Status.OPEN)
            throw ApiException.conflict("Payment already requested");
        if (orderItems.existsByOrderSessionAndStatusIn(session, NOT_SERVED_YET)) {
            throw ApiException.conflict("Submit drafts and serve or resolve all items first");
        }
        Bill bill = billing.bill(session);
        billing.recalculate(bill);
        if (bill.getTotal().signum() <= 0) throw ApiException.conflict("Bill has no payable items");

        session.setStatus(TableSession.Status.PAYMENT_REQUESTED);
        bill.setRequestedAt(Instant.now());
        events.notify(
                Role.CASHIER,
                "PAYMENT_REQUEST",
                "Payment requested",
                session.getTable().getCode(),
                id(bill));
    }

    @Override
    public void withdrawPaymentRequest(UUID sessionId, Employee waiter) {
        TableSession session = ownSession(sessionId, waiter);
        if (session.getStatus() != TableSession.Status.PAYMENT_REQUESTED)
            throw ApiException.conflict("Payment not requested");
        Bill bill = billing.bill(session);
        billing.editable(bill);

        session.setStatus(TableSession.Status.OPEN);
        bill.setRequestedAt(null);
        events.notify(
                Role.CASHIER,
                "PAYMENT_REQUEST_WITHDRAWN",
                "Payment request withdrawn",
                session.getTable().getCode(),
                id(bill));
    }

    // Optional bookkeeping: payment already closed the session and put the table in CLEANING.
    @Override
    public void guestLeft(UUID sessionId, boolean confirmed, Employee waiter) {
        TableSession session = ownedSession(sessionId, waiter);
        if (!confirmed
                || session.getGuestLeftAt() != null
                || billing.bill(session).getStatus() != Bill.Status.PAID) {
            throw ApiException.conflict("Confirm departure after payment");
        }
        session.setGuestLeftAt(Instant.now());
    }

    @Override
    public void clean(UUID sessionId, boolean confirmed, Employee waiter) {
        TableSession session = ownedSession(sessionId, waiter);
        if (!confirmed
                || session.getStatus() != TableSession.Status.CLOSED
                || session.getTable().getStatus() != TableStatus.CLEANING) {
            throw ApiException.conflict("Table is not waiting to be cleaned");
        }
        session.getTable().setStatus(TableStatus.AVAILABLE);
        events.notify(
                Role.RECEPTIONIST,
                "TABLE_READY",
                "Bàn " + session.getTable().getCode() + " đã sẵn sàng",
                "Đã dọn xong",
                id(session.getTable()));
    }

    private TableSession ownedSession(UUID sessionId, Employee waiter) {
        TableSession session = db.get(TableSession.class, sessionId.toString());
        if (!same(session.getWaiter(), waiter)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Session belongs to another waiter");
        }
        return session;
    }

    private TableSession ownSession(UUID sessionId, Employee waiter) {
        TableSession session = ownedSession(sessionId, waiter);
        if (session.getStatus() == TableSession.Status.CLOSED)
            throw ApiException.conflict("Session is closed");
        return session;
    }

    private TableSession orderingSession(UUID sessionId, Employee waiter) {
        TableSession session = ownSession(sessionId, waiter);
        if (session.getStatus() != TableSession.Status.OPEN || session.getGuestLeftAt() != null) {
            throw ApiException.conflict("Ordering is closed");
        }
        billing.editable(billing.bill(session));
        return session;
    }

    private void requireAvailable(MenuItem menu) {
        if (menu.getStatus() != MenuItemStatus.AVAILABLE || !menu.getCategory().isVisible()) {
            throw ApiException.conflict("Menu item unavailable");
        }
    }

    private OrderItem ownDraft(TableSession session, Employee waiter, UUID draftId) {
        OrderItem draft = db.get(OrderItem.class, draftId.toString());
        Order cart = draft.getOrder();
        if (draft.getStatus() != OrderItem.Status.DRAFT
                || !same(cart.getSession(), session)
                || !same(cart.getWaiter(), waiter)) {
            throw invalid("Draft does not belong to this session");
        }
        return draft;
    }

    private OrderItem sessionItem(TableSession session, UUID itemId) {
        OrderItem item = db.get(OrderItem.class, itemId.toString());
        if (!same(item.getOrder().getSession(), session))
            throw invalid("Item belongs to another session");
        return item;
    }

    private void addToCart(
            TableSession session,
            Employee waiter,
            MenuItem menu,
            BigDecimal quantity,
            String note) {
        Order cart =
                orders.findBySessionAndWaiterAndStatus(session, waiter, Order.Status.DRAFT)
                        .orElseGet(() -> newCart(session, waiter));
        OrderItem item = new OrderItem();
        item.setOrder(cart);
        item.setMenuItem(menu);
        item.setItemName(menu.getName());
        item.setUnit(menu.getPriceUnit().getValue());
        item.setUnitPrice(menu.getPrice());
        item.setQuantity(quantity);
        item.setNote(note);
        item.setStatus(OrderItem.Status.DRAFT);
        orderItems.save(item);
    }

    private Order newCart(TableSession session, Employee waiter) {
        Order cart = new Order();
        cart.setSession(session);
        cart.setWaiter(waiter);
        return orders.save(cart);
    }

    private static UUID uuid(Map<String, Object> command, String key) {
        try {
            return UUID.fromString(required(command, key, 100));
        } catch (IllegalArgumentException e) {
            throw invalid("Invalid " + key);
        }
    }

    private static boolean isConfirmed(Map<String, Object> command) {
        return Boolean.TRUE.equals(command.get("confirmed"));
    }

    private static boolean same(ImmutableEntity a, ImmutableEntity b) {
        return a != null && b != null && a.getId().equals(b.getId());
    }
}
