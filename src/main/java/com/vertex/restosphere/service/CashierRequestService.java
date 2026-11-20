package com.vertex.restosphere.service;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.dto.request.CashierAdjustmentRequestRequest;
import com.vertex.restosphere.dto.request.CashierReasonRequest;
import com.vertex.restosphere.dto.request.CashierReturnPaymentRequest;
import com.vertex.restosphere.dto.response.CashierBillResponse;
import com.vertex.restosphere.dto.response.CashierRequestResponse;
import com.vertex.restosphere.entity.ApprovalRequest;
import com.vertex.restosphere.entity.Bill;
import com.vertex.restosphere.entity.OrderItem;
import com.vertex.restosphere.entity.Payment;
import com.vertex.restosphere.entity.TableSession;
import com.vertex.restosphere.entity.User;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.repository.ApprovalRequestRepository;
import com.vertex.restosphere.repository.BillRepository;
import com.vertex.restosphere.repository.OrderItemRepository;
import com.vertex.restosphere.repository.PaymentRepository;
import com.vertex.restosphere.security.CurrentUserService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cashier requests (REST): list, adjustment, payment void, withdraw, and returning a payment request to the
 * waiter. Validation order and error codes follow the legacy commands; the money helpers come from BillingWorkflow.
 */
@Service
@RequiredArgsConstructor
public class CashierRequestService {

    private static final Set<ApprovalRequest.Type> TYPES = Set.of(ApprovalRequest.Type.DISCOUNT,
            ApprovalRequest.Type.BILL_ADJUSTMENT, ApprovalRequest.Type.PAYMENT_VOID);
    private static final Set<OrderItem.Status> REMOVED = Set.of(OrderItem.Status.CANCELLED,
            OrderItem.Status.REJECTED, OrderItem.Status.VOIDED);

    private record Ref(UUID billId, UUID paymentId, UUID itemId) {}

    private final CurrentUserService currentUser;
    private final RestaurantLock restaurantLock;
    private final WorkspaceStore store;
    private final BillingWorkflow billing;
    private final CashierWriteSupport writes;
    private final CashierBillService billViews;
    private final ApprovalRequestRepository requests;
    private final BillRepository bills;
    private final PaymentRepository payments;
    private final OrderItemRepository orderItems;

    /** Every discount, adjustment and void request, like the legacy snapshot. */
    @Transactional(readOnly = true)
    public List<CashierRequestResponse> list(UUID actorId) {
        currentUser.require(actorId, Role.ADMIN, Role.CASHIER);
        return toResponses(requests.findByTypeInOrderByCreatedAtAsc(TYPES));
    }

    @Transactional
    public CashierRequestResponse requestAdjustment(UUID actorId, UUID billId, CashierAdjustmentRequestRequest req) {
        User user = currentUser.require(actorId, Role.ADMIN, Role.CASHIER);
        restaurantLock.acquire();
        Bill bill = store.get(Bill.class, billId.toString());
        billing.editable(bill);
        String itemId = req.itemId() == null ? "" : req.itemId().trim();
        if (itemId.isEmpty() || itemId.length() > 100) {
            throw WorkspaceStore.invalid("itemId is required (max 100)");
        }
        OrderItem item = store.get(OrderItem.class, itemId);
        if (!item.getOrder().getSession().getId().equals(bill.getSession().getId())
                || REMOVED.contains(item.getStatus())) {
            throw WorkspaceStore.invalid("Invalid bill line");
        }
        ApprovalRequest r = billing.request(ApprovalRequest.Type.BILL_ADJUSTMENT, "ORDER_ITEM", item.getId(),
                BillingWorkflow.whole(item.getUnitPrice().multiply(item.getQuantity())), reason(req.reason()), user);
        writes.commit(user, "request-adjustment");
        store.flush();
        return CashierRequestResponse.from(r, bill.getId(), null, item.getId());
    }

    @Transactional
    public CashierRequestResponse requestVoid(UUID actorId, UUID paymentId, CashierReasonRequest req) {
        User user = currentUser.require(actorId, Role.ADMIN, Role.CASHIER);
        restaurantLock.acquire();
        Payment payment = store.get(Payment.class, paymentId.toString());
        if (payment.getStatus() != Payment.Status.COMPLETED) {
            throw ApiException.conflict("Only successful payments may be voided");
        }
        ApprovalRequest r = billing.request(ApprovalRequest.Type.PAYMENT_VOID, "PAYMENT", payment.getId(),
                payment.getAmount(), reason(req.reason()), user);
        writes.commit(user, "request-void");
        store.flush();
        return CashierRequestResponse.from(r, payment.getBill().getId(), payment.getId(), null);
    }

    @Transactional
    public CashierRequestResponse withdraw(UUID actorId, UUID requestId) {
        User user = currentUser.require(actorId, Role.ADMIN, Role.CASHIER);
        restaurantLock.acquire();
        ApprovalRequest r = store.get(ApprovalRequest.class, requestId.toString());
        if (!r.getRequestedBy().getId().equals(user.getId()) || r.getStatus() != ApprovalRequest.Status.PENDING) {
            throw ApiException.conflict("Request cannot be withdrawn");
        }
        r.setStatus(ApprovalRequest.Status.WITHDRAWN);
        writes.commit(user, "withdraw-request");
        store.flush();
        return toResponses(List.of(r)).getFirst();
    }

    /** Give the bill back to the waiter: the session returns to OPEN and the payment request is cleared. */
    @Transactional
    public CashierBillResponse returnPaymentRequest(UUID actorId, UUID billId, CashierReturnPaymentRequest req) {
        User user = currentUser.require(actorId, Role.ADMIN, Role.CASHIER);
        restaurantLock.acquire();
        Bill bill = store.get(Bill.class, billId.toString());
        billing.version(bill, Map.of("billVersion", req.billVersion()));
        billing.recalculate(bill);
        billing.editable(bill);
        reason(req.reason());
        if (bill.getSession().getStatus() != TableSession.Status.PAYMENT_REQUESTED) {
            throw ApiException.conflict("Payment was not requested");
        }
        bill.getSession().setStatus(TableSession.Status.OPEN);
        bill.setRequestedAt(null);
        writes.commit(user, "return-payment-request");
        store.flush();
        return billViews.view(billId);
    }

    private static String reason(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty() || value.length() > 1000) {
            throw WorkspaceStore.invalid("reason is required (max 1000)");
        }
        return value;
    }

    /** Resolves bill / payment / item ids of many requests with a handful of queries (no N+1). */
    private List<CashierRequestResponse> toResponses(List<ApprovalRequest> found) {
        if (found.isEmpty()) {
            return List.of();
        }
        List<UUID> paymentIds = new ArrayList<>();
        List<UUID> itemIds = new ArrayList<>();
        for (ApprovalRequest r : found) {
            if ("PAYMENT".equals(r.getTargetType())) {
                paymentIds.add(r.getTargetId());
            } else if ("ORDER_ITEM".equals(r.getTargetType())) {
                itemIds.add(r.getTargetId());
            }
        }
        Map<UUID, UUID> billByPayment = new HashMap<>();
        if (!paymentIds.isEmpty()) {
            payments.findAllById(paymentIds).forEach(p -> billByPayment.put(p.getId(), p.getBill().getId()));
        }
        Map<UUID, UUID> sessionByItem = new HashMap<>();
        if (!itemIds.isEmpty()) {
            orderItems.findWithOrderByIdIn(itemIds)
                    .forEach(i -> sessionByItem.put(i.getId(), i.getOrder().getSession().getId()));
        }
        Map<UUID, UUID> billBySession = new HashMap<>();
        if (!sessionByItem.isEmpty()) {
            bills.findBySessionIdIn(sessionByItem.values().stream().distinct().toList())
                    .forEach(b -> billBySession.put(b.getSession().getId(), b.getId()));
        }
        return found.stream().map(r -> {
            Ref ref = switch (r.getTargetType()) {
                case "BILL" -> new Ref(r.getTargetId(), null, null);
                case "PAYMENT" -> new Ref(billByPayment.get(r.getTargetId()), r.getTargetId(), null);
                default -> new Ref(billBySession.get(sessionByItem.get(r.getTargetId())), null, r.getTargetId());
            };
            return CashierRequestResponse.from(r, ref.billId(), ref.paymentId(), ref.itemId());
        }).toList();
    }
}
