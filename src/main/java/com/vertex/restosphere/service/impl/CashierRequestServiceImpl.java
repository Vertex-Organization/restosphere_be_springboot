package com.vertex.restosphere.service.impl;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.common.util.Values;
import com.vertex.restosphere.dto.request.CashierAdjustmentRequestRequest;
import com.vertex.restosphere.dto.request.CashierReasonRequest;
import com.vertex.restosphere.dto.request.CashierReturnPaymentRequest;
import com.vertex.restosphere.dto.response.CashierBillResponse;
import com.vertex.restosphere.dto.response.CashierRequestResponse;
import com.vertex.restosphere.entity.ApprovalRequest;
import com.vertex.restosphere.entity.Bill;
import com.vertex.restosphere.entity.Employee;
import com.vertex.restosphere.entity.OrderItem;
import com.vertex.restosphere.entity.Payment;
import com.vertex.restosphere.entity.TableSession;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.repository.ApprovalRequestRepository;
import com.vertex.restosphere.repository.BillRepository;
import com.vertex.restosphere.repository.OrderItemRepository;
import com.vertex.restosphere.repository.PaymentRepository;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.BillingWorkflow;
import com.vertex.restosphere.service.CashierBillService;
import com.vertex.restosphere.service.CashierRequestService;
import com.vertex.restosphere.service.RestaurantLock;
import com.vertex.restosphere.service.WorkspaceCommit;
import com.vertex.restosphere.service.WorkspaceStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CashierRequestServiceImpl implements CashierRequestService {
    private static final Set<ApprovalRequest.Type> TYPES =
            Set.of(
                    ApprovalRequest.Type.DISCOUNT,
                    ApprovalRequest.Type.BILL_ADJUSTMENT,
                    ApprovalRequest.Type.PAYMENT_VOID);
    private static final Set<OrderItem.Status> REMOVED = OrderItem.NOT_BILLABLE;

    private record Ref(UUID billId, UUID paymentId, UUID itemId) {}

    private final CurrentUserService currentUser;
    private final RestaurantLock restaurantLock;
    private final WorkspaceStore store;
    private final BillingWorkflow billing;
    private final WorkspaceCommit writes;
    private final CashierBillService billViews;
    private final ApprovalRequestRepository requests;
    private final BillRepository bills;
    private final PaymentRepository payments;
    private final OrderItemRepository orderItems;

    @Transactional(readOnly = true)
    @Override
    public List<CashierRequestResponse> list(UUID actorId) {
        currentUser.require(actorId, Role.ADMIN, Role.CASHIER);
        return toResponses(requests.findByTypeInOrderByCreatedAtAsc(TYPES));
    }

    @Transactional
    @Override
    public CashierRequestResponse requestAdjustment(
            UUID actorId, UUID billId, CashierAdjustmentRequestRequest req) {
        Employee user = currentUser.require(actorId, Role.ADMIN, Role.CASHIER);
        restaurantLock.acquire();
        Bill bill = store.get(Bill.class, billId.toString());
        billing.editable(bill);
        String itemId = req.itemId() == null ? "" : req.itemId().trim();
        if (itemId.isEmpty() || itemId.length() > 100) {
            throw Values.invalid("itemId is required (max 100)");
        }
        OrderItem item = store.get(OrderItem.class, itemId);
        if (!item.getOrder().getSession().getBill().getId().equals(bill.getId())
                || REMOVED.contains(item.getStatus())) {
            throw Values.invalid("Invalid bill line");
        }
        ApprovalRequest r =
                billing.request(
                        ApprovalRequest.Type.BILL_ADJUSTMENT,
                        "ORDER_ITEM",
                        item.getId(),
                        Values.whole(item.getUnitPrice().multiply(item.getQuantity())),
                        reason(req.reason()),
                        user);
        writes.commit(user, "request-adjustment");
        store.flush();
        return CashierRequestResponse.from(r, bill.getId(), null, item.getId());
    }

    @Transactional
    @Override
    public CashierRequestResponse requestVoid(
            UUID actorId, UUID paymentId, CashierReasonRequest req) {
        Employee user = currentUser.require(actorId, Role.ADMIN, Role.CASHIER);
        restaurantLock.acquire();
        Payment payment = store.get(Payment.class, paymentId.toString());
        if (payment.getStatus() != Payment.Status.COMPLETED) {
            throw ApiException.conflict("Only successful payments may be voided");
        }
        ApprovalRequest r =
                billing.request(
                        ApprovalRequest.Type.PAYMENT_VOID,
                        "PAYMENT",
                        payment.getId(),
                        payment.getAmount(),
                        reason(req.reason()),
                        user);
        writes.commit(user, "request-void");
        store.flush();
        return CashierRequestResponse.from(r, payment.getBill().getId(), payment.getId(), null);
    }

    @Transactional
    @Override
    public CashierRequestResponse withdraw(UUID actorId, UUID requestId) {
        Employee user = currentUser.require(actorId, Role.ADMIN, Role.CASHIER);
        restaurantLock.acquire();
        ApprovalRequest r = store.get(ApprovalRequest.class, requestId.toString());
        if (!r.getRequestedBy().getId().equals(user.getId())
                || r.getStatus() != ApprovalRequest.Status.PENDING) {
            throw ApiException.conflict("Request cannot be withdrawn");
        }
        r.setStatus(ApprovalRequest.Status.WITHDRAWN);
        writes.commit(user, "withdraw-request");
        store.flush();
        return toResponses(List.of(r)).getFirst();
    }

    @Transactional
    @Override
    public CashierBillResponse returnPaymentRequest(
            UUID actorId, UUID billId, CashierReturnPaymentRequest req) {
        Employee user = currentUser.require(actorId, Role.ADMIN, Role.CASHIER);
        restaurantLock.acquire();
        Bill bill = store.get(Bill.class, billId.toString());
        billing.requireVersion(bill, req.billVersion());
        billing.recalculate(bill);
        billing.editable(bill);
        reason(req.reason());
        if (!bill.awaitingPayment()) {
            throw ApiException.conflict("Payment was not requested");
        }
        bill.getSessions().forEach(s -> s.setStatus(TableSession.Status.OPEN));
        bill.setRequestedAt(null);
        writes.commit(user, "return-payment-request");
        store.flush();
        return billViews.view(billId);
    }

    private static String reason(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty() || value.length() > 1000) {
            throw Values.invalid("reason is required (max 1000)");
        }
        return value;
    }

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
            payments.findAllById(paymentIds)
                    .forEach(p -> billByPayment.put(p.getId(), p.getBill().getId()));
        }
        Map<UUID, UUID> billByItem = new HashMap<>();
        if (!itemIds.isEmpty()) {
            orderItems
                    .findWithOrderByIdIn(itemIds)
                    .forEach(
                            i ->
                                    billByItem.put(
                                            i.getId(),
                                            i.getOrder().getSession().getBill().getId()));
        }
        return found.stream()
                .map(
                        r -> {
                            Ref ref =
                                    switch (r.getTargetType()) {
                                        case "BILL" -> new Ref(r.getTargetId(), null, null);
                                        case "PAYMENT" ->
                                                new Ref(
                                                        billByPayment.get(r.getTargetId()),
                                                        r.getTargetId(),
                                                        null);
                                        default ->
                                                new Ref(
                                                        billByItem.get(r.getTargetId()),
                                                        null,
                                                        r.getTargetId());
                                    };
                            return CashierRequestResponse.from(
                                    r, ref.billId(), ref.paymentId(), ref.itemId());
                        })
                .toList();
    }
}
