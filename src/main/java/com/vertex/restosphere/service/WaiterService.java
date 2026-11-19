package com.vertex.restosphere.service;

import static com.vertex.restosphere.service.ReservationWorkflow.number;
import static com.vertex.restosphere.service.ReservationWorkflow.required;
import static com.vertex.restosphere.service.ReservationWorkflow.text;
import static com.vertex.restosphere.service.ReservationWorkflow.validateQuantity;
import static com.vertex.restosphere.service.WorkspaceStore.id;
import static com.vertex.restosphere.service.WorkspaceStore.invalid;

import com.vertex.restosphere.common.enums.MenuItemStatus;
import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.common.enums.TableStatus;
import com.vertex.restosphere.entity.*;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.repository.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Waiter (WAITER role) operations on a table session (TableSession):
 * accept table -> cart -> submit to kitchen -> serve -> request payment -> guests leave -> clean.
 *
 * <p>Two entry points, both running inside the transaction and lock of {@link WorkspaceService}:
 * <ul>
 *   <li>Dedicated REST: {@code WaiterOrderController} calls the public methods below directly.</li>
 *   <li>Generic endpoint {@code POST /api/v1/workspaces/staff/commands}: goes through {@link #handle}.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class WaiterService {

    private static final Set<OrderItem.Status> NOT_SERVED_YET =
            EnumSet.of(OrderItem.Status.SUBMITTED, OrderItem.Status.PREPARING, OrderItem.Status.READY);
    private static final Set<OrderItem.Status> CANCELLABLE =
            EnumSet.of(OrderItem.Status.PREPARING, OrderItem.Status.READY);
    private static final int MAX_NOTE = 1000;

    private final WorkspaceStore db;
    private final OrderDraftRepository drafts;
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final ReservationItemRepository reservationItems;
    private final ApprovalRequestRepository approvals;
    private final BillingWorkflow billing;
    private final ServiceWorkflow events;

    /** Dispatches a {@code type} command from the generic workspace endpoint. */
    public void handle(String type, Map<String, Object> command, User waiter) {
        UUID session = uuid(command, "sessionId");
        switch (type) {
            case "accept" -> accept(session, waiter);
            case "add-draft" -> addDraft(session, uuid(command, "menuItemId"), waiter);
            case "edit-draft" -> editDraft(session, uuid(command, "draftId"),
                    number(command, "quantity"), text(command, "note"), waiter);
            case "remove-draft" -> removeDraft(session, uuid(command, "draftId"), waiter);
            case "submit" -> submit(session, waiter);
            case "serve" -> serve(session, uuid(command, "itemId"), waiter);
            case "request-cancel" -> requestCancel(session, uuid(command, "itemId"),
                    required(command, "reason", MAX_NOTE), waiter);
            case "request-priority" -> requestPriority(session, uuid(command, "orderId"),
                    required(command, "reason", MAX_NOTE), waiter);
            case "request-payment" -> requestPayment(session, waiter);
            case "withdraw-payment-request" -> withdrawPaymentRequest(session, waiter);
            case "guest-left" -> guestLeft(session, isConfirmed(command), waiter);
            case "clean" -> clean(session, isConfirmed(command), waiter);
            default -> throw invalid("Unsupported waiter command");
        }
    }

    // ---- Table acceptance ----

    /** Accepts a table handed over by reception; the guest's pre-ordered items are copied into the cart. */
    public void accept(UUID sessionId, User waiter) {
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
            for (ReservationItem preorder : reservationItems.findByReservation(session.getReservation())) {
                drafts.save(newDraft(session, waiter, preorder.getMenuItem(), preorder.getQuantity(), preorder.getNote()));
            }
        }
    }

    // ---- Cart ----

    public void addDraft(UUID sessionId, UUID menuItemId, User waiter) {
        TableSession session = orderingSession(sessionId, waiter);
        MenuItem menu = db.get(MenuItem.class, menuItemId.toString());
        requireAvailable(menu);
        drafts.save(newDraft(session, waiter, menu, BigDecimal.ONE, ""));
    }

    public void editDraft(UUID sessionId, UUID draftId, BigDecimal quantity, String note, User waiter) {
        TableSession session = orderingSession(sessionId, waiter);
        OrderDraft draft = ownDraft(session, waiter, draftId);
        String cleanNote = note == null ? "" : note.trim();
        validateQuantity(draft.getMenuItem(), quantity);
        if (cleanNote.length() > MAX_NOTE) throw invalid("Note too long");
        draft.setQuantity(quantity);
        draft.setNote(cleanNote);
    }

    public void removeDraft(UUID sessionId, UUID draftId, User waiter) {
        TableSession session = orderingSession(sessionId, waiter);
        drafts.delete(ownDraft(session, waiter, draftId));
    }

    /** Sends the whole cart as a new order round; prices are locked at submit time. */
    public void submit(UUID sessionId, User waiter) {
        TableSession session = orderingSession(sessionId, waiter);
        var cart = drafts.findBySessionAndWaiterOrderByCreatedAt(session, waiter);
        if (cart.isEmpty()) throw invalid("No draft items");

        Order order = new Order();
        order.setSession(session);
        order.setWaiter(waiter);
        order.setRoundNumber(orders.findLastRoundNumber(session) + 1);
        orders.save(order);

        for (OrderDraft draft : cart) {
            MenuItem menu = draft.getMenuItem();
            requireAvailable(menu);
            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setMenuItem(menu);
            item.setItemName(menu.getName());
            item.setUnit(menu.getPriceUnit());
            item.setUnitPrice(menu.getPrice());
            item.setQuantity(draft.getQuantity());
            item.setNote(draft.getNote());
            orderItems.save(item);
        }
        drafts.deleteAll(cart);

        billing.recalculate(billing.bill(session));
        events.notify(Role.KITCHEN, "NEW_ORDER", "New order", session.getTable().getCode(), id(order));
    }

    // ---- Submitted items ----

    public void serve(UUID sessionId, UUID itemId, User waiter) {
        TableSession session = ownSession(sessionId, waiter);
        OrderItem item = sessionItem(session, itemId);
        if (item.getStatus() != OrderItem.Status.READY) throw ApiException.conflict("Item is not ready");
        item.setStatus(OrderItem.Status.SERVED);
        item.setServedAt(Instant.now());
    }

    /** Items being prepared or ready can only be cancelled with manager approval. */
    public void requestCancel(UUID sessionId, UUID itemId, String reason, User waiter) {
        TableSession session = orderingSession(sessionId, waiter);
        OrderItem item = sessionItem(session, itemId);
        if (!CANCELLABLE.contains(item.getStatus())) throw ApiException.conflict("Item cannot be cancelled");
        if (approvals.existsByTypeAndTargetIdAndStatus(
                ApprovalRequest.Type.CANCEL_ITEM, item.getId(), ApprovalRequest.Status.PENDING)) {
            throw ApiException.conflict("A cancel request is already pending");
        }
        BigDecimal amount = BillingWorkflow.whole(item.getUnitPrice().multiply(item.getQuantity()));
        billing.request(ApprovalRequest.Type.CANCEL_ITEM, "ORDER_ITEM", item.getId(), amount, reason, waiter);
    }

    public void requestPriority(UUID sessionId, UUID orderId, String reason, User waiter) {
        TableSession session = ownSession(sessionId, waiter);
        Order order = db.get(Order.class, orderId.toString());
        if (!same(order.getSession(), session)) throw invalid("Order belongs to another session");
        order.setPriorityAt(Instant.now());
        order.setPriorityBy(waiter);
        order.setPriorityReason(reason);
        events.notify(Role.KITCHEN, "PRIORITY", "Priority requested", reason, id(order));
    }

    // ---- Payment ----

    /** Allowed only when the cart is empty, every valid item is served and the bill is non-zero. */
    public void requestPayment(UUID sessionId, User waiter) {
        TableSession session = ownSession(sessionId, waiter);
        if (session.getStatus() != TableSession.Status.OPEN) throw ApiException.conflict("Payment already requested");
        if (drafts.existsBySession(session) || orderItems.existsByOrderSessionAndStatusIn(session, NOT_SERVED_YET)) {
            throw ApiException.conflict("Submit drafts and serve or resolve all items first");
        }
        Bill bill = billing.bill(session);
        billing.recalculate(bill);
        if (bill.getTotal().signum() <= 0) throw ApiException.conflict("Bill has no payable items");

        session.setStatus(TableSession.Status.PAYMENT_REQUESTED);
        bill.setRequestedAt(Instant.now());
        events.notify(Role.CASHIER, "PAYMENT_REQUEST", "Payment requested", session.getTable().getCode(), id(bill));
    }

    public void withdrawPaymentRequest(UUID sessionId, User waiter) {
        TableSession session = ownSession(sessionId, waiter);
        if (session.getStatus() != TableSession.Status.PAYMENT_REQUESTED) throw ApiException.conflict("Payment not requested");
        Bill bill = billing.bill(session);
        billing.editable(bill);

        session.setStatus(TableSession.Status.OPEN);
        bill.setRequestedAt(null);
        events.notify(Role.CASHIER, "PAYMENT_REQUEST_WITHDRAWN", "Payment request withdrawn",
                session.getTable().getCode(), id(bill));
    }

    // ---- End of service ----

    /** Guests left after paying: the table moves to cleaning. */
    public void guestLeft(UUID sessionId, boolean confirmed, User waiter) {
        TableSession session = ownSession(sessionId, waiter);
        if (!confirmed || session.getGuestLeftAt() != null || billing.bill(session).getStatus() != Bill.Status.PAID) {
            throw ApiException.conflict("Confirm departure after payment");
        }
        session.setGuestLeftAt(Instant.now());
        session.getTable().setStatus(TableStatus.CLEANING);
    }

    /** Cleaning done: closes the session, frees the table and completes the reservation. */
    public void clean(UUID sessionId, boolean confirmed, User waiter) {
        TableSession session = ownSession(sessionId, waiter);
        if (!confirmed || session.getGuestLeftAt() == null || session.getTable().getStatus() != TableStatus.CLEANING) {
            throw ApiException.conflict("Guests must leave before cleaning");
        }
        session.setEndedAt(Instant.now());
        session.setStatus(TableSession.Status.CLOSED);
        session.getTable().setStatus(TableStatus.AVAILABLE);
        if (session.getReservation() != null) session.getReservation().setStatus(Reservation.Status.COMPLETED);
    }

    // ---- Shared checks ----

    /** Session accepted by this waiter and not closed yet. */
    private TableSession ownSession(UUID sessionId, User waiter) {
        TableSession session = db.get(TableSession.class, sessionId.toString());
        if (!same(session.getWaiter(), waiter)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Session belongs to another waiter");
        }
        if (session.getStatus() == TableSession.Status.CLOSED) throw ApiException.conflict("Session is closed");
        return session;
    }

    /** Ordering is open only before payment is requested, while guests are seated and the bill has no payments. */
    private TableSession orderingSession(UUID sessionId, User waiter) {
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

    private OrderDraft ownDraft(TableSession session, User waiter, UUID draftId) {
        OrderDraft draft = db.get(OrderDraft.class, draftId.toString());
        if (!same(draft.getSession(), session) || !same(draft.getWaiter(), waiter)) {
            throw invalid("Draft does not belong to this session");
        }
        return draft;
    }

    private OrderItem sessionItem(TableSession session, UUID itemId) {
        OrderItem item = db.get(OrderItem.class, itemId.toString());
        if (!same(item.getOrder().getSession(), session)) throw invalid("Item belongs to another session");
        return item;
    }

    private static OrderDraft newDraft(TableSession session, User waiter, MenuItem menu, BigDecimal quantity, String note) {
        OrderDraft draft = new OrderDraft();
        draft.setSession(session);
        draft.setWaiter(waiter);
        draft.setMenuItem(menu);
        draft.setQuantity(quantity);
        draft.setNote(note);
        return draft;
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

    /** Compares by id so Hibernate lazy proxies do not matter. */
    private static boolean same(BaseEntity a, BaseEntity b) {
        return a != null && b != null && a.getId().equals(b.getId());
    }
}
