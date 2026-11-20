package com.vertex.restosphere.service;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.dto.response.CashierBillResponse;
import com.vertex.restosphere.dto.response.CashierConfigResponse;
import com.vertex.restosphere.dto.response.CashierItemResponse;
import com.vertex.restosphere.dto.response.CashierPaymentResponse;
import com.vertex.restosphere.entity.AuditLog;
import com.vertex.restosphere.entity.Bill;
import com.vertex.restosphere.entity.Payment;
import com.vertex.restosphere.entity.RestaurantSettings;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.repository.AuditLogRepository;
import com.vertex.restosphere.repository.BillRepository;
import com.vertex.restosphere.repository.OrderItemRepository;
import com.vertex.restosphere.repository.PaymentRepository;
import com.vertex.restosphere.repository.RestaurantSettingsRepository;
import com.vertex.restosphere.security.CurrentUserService;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Bills and billing config for the cashier (REST, read only). Totals are the stored values, like the snapshot. */
@Service
@RequiredArgsConstructor
public class CashierBillService {

    private static final Set<String> STATUSES = Set.of("UNPAID", "PAYMENT_REQUESTED", "PAID");

    private final CurrentUserService currentUser;
    private final BillRepository bills;
    private final PaymentRepository payments;
    private final OrderItemRepository orderItems;
    private final AuditLogRepository auditLogs;
    private final RestaurantSettingsRepository settings;

    @Transactional(readOnly = true)
    public List<CashierBillResponse> list(UUID actorId, String status) {
        currentUser.require(actorId, Role.ADMIN, Role.CASHIER);
        String wanted = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        if (wanted != null && !STATUSES.contains(wanted)) {
            throw WorkspaceStore.invalid("Invalid status");
        }
        List<Bill> found = bills.findAllByOrderByCreatedAtAsc().stream()
                .filter(b -> wanted == null || CashierBillResponse.statusOf(b).equals(wanted)).toList();
        if (found.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = found.stream().map(Bill::getId).toList();
        Map<UUID, List<Payment>> paymentsByBill = payments.findByBillIdInOrderByCreatedAtAsc(ids).stream()
                .collect(Collectors.groupingBy(p -> p.getBill().getId()));
        Map<UUID, List<CashierBillResponse.Event>> history = history(ids);
        return found.stream().map(b -> CashierBillResponse.from(b, paid(paymentsByBill.get(b.getId())),
                history.getOrDefault(b.getId(), List.of()), null, null)).toList();
    }

    @Transactional(readOnly = true)
    public CashierBillResponse detail(UUID actorId, UUID billId) {
        currentUser.require(actorId, Role.ADMIN, Role.CASHIER);
        Bill bill = bills.findDetailedById(billId).orElseThrow(() -> ApiException.notFound("Bill"));
        List<Payment> billPayments = payments.findByBillIdInOrderByCreatedAtAsc(List.of(billId));
        List<CashierItemResponse> items = orderItems
                .findByOrderSessionIdOrderByCreatedAtAsc(bill.getSession().getId()).stream()
                .map(CashierItemResponse::from).toList();
        return CashierBillResponse.from(bill, paid(billPayments),
                history(List.of(billId)).getOrDefault(billId, List.of()), items,
                billPayments.stream().map(CashierPaymentResponse::from).toList());
    }

    @Transactional(readOnly = true)
    public CashierConfigResponse config(UUID actorId) {
        currentUser.require(actorId, Role.ADMIN, Role.CASHIER);
        RestaurantSettings s = settings.findBySingletonKey(1).orElseGet(RestaurantSettings::new);
        boolean transfer = s.getBankAccountNumber() != null && !s.getBankAccountNumber().isBlank();
        CashierConfigResponse.Bank bank = transfer ? new CashierConfigResponse.Bank(
                s.getBankName() == null ? "" : s.getBankName(), s.getBankAccountNumber(),
                s.getBankAccountName() == null ? "" : s.getBankAccountName()) : null;
        return new CashierConfigResponse(s.getName(), s.getAddress(), s.getServiceChargeRate().movePointRight(2),
                s.getVatRate().movePointRight(2), Map.of("CASH", true, "TRANSFER", transfer, "CARD", false), bank);
    }

    private static BigDecimal paid(Collection<Payment> list) {
        if (list == null) {
            return BigDecimal.ZERO;
        }
        return list.stream().filter(p -> p.getStatus() == Payment.Status.COMPLETED).map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<UUID, List<CashierBillResponse.Event>> history(List<UUID> billIds) {
        Map<UUID, List<CashierBillResponse.Event>> result = new java.util.HashMap<>();
        for (AuditLog a : auditLogs.findByTargetIdInOrderByCreatedAtAsc(billIds)) {
            result.computeIfAbsent(a.getTargetId(), k -> new java.util.ArrayList<>())
                    .add(new CashierBillResponse.Event(a.getCreatedAt(),
                            a.getActor() == null ? "" : a.getActor().getId().toString(), a.getDescription()));
        }
        return result;
    }
}
