package com.vertex.restosphere.service.impl;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.dto.request.ApprovalDecisionRequest;
import com.vertex.restosphere.dto.response.ApprovalResponse;
import com.vertex.restosphere.entity.ApprovalRequest;
import com.vertex.restosphere.entity.AuditLog;
import com.vertex.restosphere.entity.Bill;
import com.vertex.restosphere.entity.MenuItem;
import com.vertex.restosphere.entity.OrderItem;
import com.vertex.restosphere.entity.Payment;
import com.vertex.restosphere.entity.Reservation;
import com.vertex.restosphere.entity.User;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.repository.ApprovalQueryRepository;
import com.vertex.restosphere.repository.AuditLogRepository;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.BillingWorkflow;
import com.vertex.restosphere.service.ManagerApprovalService;
import com.vertex.restosphere.service.RestaurantLock;
import com.vertex.restosphere.service.ServiceWorkflow;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagerApprovalServiceImpl implements ManagerApprovalService {

    private final CurrentUserService currentUser;
    private final RestaurantLock restaurantLock;
    private final ApprovalQueryRepository approvals;
    private final BillingWorkflow billingWorkflow;
    private final ServiceWorkflow serviceWorkflow;
    private final AuditLogRepository auditLogs;

    @Transactional(readOnly = true)
    @Override
    public List<ApprovalResponse> list(UUID actorId, ApprovalRequest.Status status, ApprovalRequest.Type type) {
        currentUser.require(actorId, Role.ADMIN, Role.MANAGER);
        Specification<ApprovalRequest> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (status != null) {
                where.add(cb.equal(root.get("status"), status));
            }
            if (type != null) {
                where.add(cb.equal(root.get("type"), type));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        return toResponses(approvals.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @Transactional
    @Override
    public ApprovalResponse decide(UUID actorId, UUID id, ApprovalDecisionRequest req) {
        restaurantLock.acquire();
        User actor = currentUser.require(actorId, Role.ADMIN, Role.MANAGER);
        String reason = req.reason().strip();
        if (reason.length() < 5) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Reason must be at least 5 characters");
        }
        ApprovalRequest r = approvals.findById(id).orElseThrow(() -> ApiException.notFound("Approval request"));
        billingWorkflow.decide(Map.of("id", id.toString(), "approve", req.approve(), "reason", reason), actor);
        serviceWorkflow.notify(r.getRequestedBy().getRole(), "REQUEST_DECIDED", "Request decided",
                r.getStatus().name(), id.toString());

        AuditLog log = new AuditLog();
        log.setActor(actor);
        log.setAction(req.approve() ? "APPROVAL_APPROVED" : "APPROVAL_REJECTED");
        log.setTargetType("APPROVAL_REQUEST");
        log.setTargetId(r.getId());
        log.setDescription((req.approve() ? "Phê duyệt" : "Từ chối") + " yêu cầu " + typeName(r.getType()) + " "
                + code(r) + ": " + reason);
        auditLogs.save(log);
        return toResponses(List.of(r)).getFirst();
    }

    private List<ApprovalResponse> toResponses(List<ApprovalRequest> list) {
        Map<UUID, Bill> bills = load(targets(list, "BILL"), approvals::findBillsWithTableByIdIn, Bill::getId);
        Map<UUID, OrderItem> items = load(targets(list, "ORDER_ITEM"), approvals::findOrderItemsWithTableByIdIn,
                OrderItem::getId);
        Map<UUID, Bill> billBySession = load(
                items.values().stream().map(i -> i.getOrder().getSession().getId()).collect(Collectors.toSet()),
                approvals::findBillsBySessionIdIn, b -> b.getSession().getId());
        Map<UUID, Payment> payments = load(targets(list, "PAYMENT"), approvals::findPaymentsWithBillByIdIn,
                Payment::getId);
        Map<UUID, MenuItem> menu = load(targets(list, "MENU_ITEM"), approvals::findMenuItemsByIdIn, MenuItem::getId);
        Map<UUID, Reservation> reservations = load(targets(list, "RESERVATION"), approvals::findReservationsByIdIn,
                Reservation::getId);

        return list.stream().map(r -> {
            UUID target = r.getTargetId();
            UUID billId = null;
            UUID paymentId = null;
            UUID itemId = null;
            String label = null;
            switch (r.getTargetType()) {
                case "BILL" -> {
                    billId = target;
                    Bill b = bills.get(target);
                    label = b == null ? null : "Hóa đơn " + b.getCode() + " · Bàn " + b.getSession().getTable().getCode();
                }
                case "ORDER_ITEM" -> {
                    itemId = target;
                    OrderItem i = items.get(target);
                    if (i != null) {
                        Bill b = billBySession.get(i.getOrder().getSession().getId());
                        billId = b == null ? null : b.getId();
                        label = i.getItemName() + " · Bàn " + i.getOrder().getSession().getTable().getCode();
                    }
                }
                case "PAYMENT" -> {
                    paymentId = target;
                    Payment p = payments.get(target);
                    if (p != null) {
                        billId = p.getBill().getId();
                        label = "Thanh toán " + p.getMethod() + " · Hóa đơn " + p.getBill().getCode();
                    }
                }
                case "MENU_ITEM" -> {
                    MenuItem m = menu.get(target);
                    label = m == null ? null : m.getName();
                }
                case "RESERVATION" -> {
                    Reservation res = reservations.get(target);
                    label = res == null ? null : res.getCode();
                }
                default -> {
                }
            }
            return new ApprovalResponse(r.getId(), code(r), r.getType(), r.getStatus(), r.getTargetType(), target,
                    label == null ? target.toString() : label, billId, paymentId, itemId, r.getAmount(),
                    r.getMenuStatus(), r.getReason(), r.getRequestedBy().getId(), r.getCreatedAt(),
                    r.getDecidedBy() == null ? null : r.getDecidedBy().getId(), r.getDecidedAt(), r.getDecisionNote());
        }).toList();
    }

    private static Set<UUID> targets(List<ApprovalRequest> list, String targetType) {
        return list.stream().filter(r -> targetType.equals(r.getTargetType())).map(ApprovalRequest::getTargetId)
                .collect(Collectors.toSet());
    }

    private static <T> Map<UUID, T> load(Set<UUID> ids, Function<Set<UUID>, List<T>> query, Function<T, UUID> key) {
        return ids.isEmpty() ? Map.of() : query.apply(ids).stream()
                .collect(Collectors.toMap(key, Function.identity(), (a, b) -> a));
    }

    private static String code(ApprovalRequest r) {
        return "R-" + r.getId().toString().substring(0, 8).toUpperCase();
    }

    private static String typeName(ApprovalRequest.Type type) {
        return switch (type) {
            case DISCOUNT -> "Giảm giá";
            case CANCEL_ITEM -> "Hủy món";
            case BILL_ADJUSTMENT -> "Điều chỉnh hóa đơn";
            case PAYMENT_VOID -> "Hủy thanh toán";
            case RESERVATION -> "Đặt bàn đặc biệt";
            case MENU_STATUS -> "Trạng thái món";
        };
    }
}
