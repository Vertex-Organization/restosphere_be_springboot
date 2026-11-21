package com.vertex.restosphere.service.impl;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.common.util.Values;
import com.vertex.restosphere.dto.response.CashierBillResponse;
import com.vertex.restosphere.dto.response.CashierConfigResponse;
import com.vertex.restosphere.dto.response.CashierItemResponse;
import com.vertex.restosphere.dto.response.CashierPaymentResponse;
import com.vertex.restosphere.dto.response.CashierReceiptResponse;
import com.vertex.restosphere.entity.AuditLog;
import com.vertex.restosphere.entity.Bill;
import com.vertex.restosphere.entity.OrderItem;
import com.vertex.restosphere.entity.Payment;
import com.vertex.restosphere.entity.RestaurantSettings;
import com.vertex.restosphere.entity.User;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.repository.AuditLogRepository;
import com.vertex.restosphere.repository.BillRepository;
import com.vertex.restosphere.repository.OrderItemRepository;
import com.vertex.restosphere.repository.PaymentRepository;
import com.vertex.restosphere.repository.RestaurantSettingsRepository;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.CashierBillService;
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

@Service
@RequiredArgsConstructor
public class CashierBillServiceImpl implements CashierBillService {
    private static final Set<String> STATUSES = Set.of("UNPAID", "PAYMENT_REQUESTED", "PAID");

    private final CurrentUserService currentUser;
    private final BillRepository bills;
    private final PaymentRepository payments;
    private final OrderItemRepository orderItems;
    private final AuditLogRepository auditLogs;
    private final RestaurantSettingsRepository settings;

    @Transactional(readOnly = true)
    @Override
    public List<CashierBillResponse> list(UUID actorId, String status) {
        currentUser.require(actorId, Role.ADMIN, Role.CASHIER);
        String wanted = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        if (wanted != null && !STATUSES.contains(wanted)) {
            throw Values.invalid("Invalid status");
        }
        List<Bill> found =
                bills.findAllByOrderByCreatedAtAsc().stream()
                        .filter(
                                b ->
                                        wanted == null
                                                || CashierBillResponse.statusOf(b).equals(wanted))
                        .toList();
        if (found.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = found.stream().map(Bill::getId).toList();
        Map<UUID, List<Payment>> paymentsByBill =
                payments.findByBillIdInOrderByCreatedAtAsc(ids).stream()
                        .collect(Collectors.groupingBy(p -> p.getBill().getId()));
        Map<UUID, List<CashierBillResponse.Event>> history = history(ids);
        return found.stream()
                .map(
                        b ->
                                CashierBillResponse.from(
                                        b,
                                        paid(paymentsByBill.get(b.getId())),
                                        history.getOrDefault(b.getId(), List.of()),
                                        null,
                                        null))
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public CashierBillResponse detail(UUID actorId, UUID billId) {
        currentUser.require(actorId, Role.ADMIN, Role.CASHIER);
        return view(billId);
    }

    @Override
    public CashierBillResponse view(UUID billId) {
        Bill bill = bills.findDetailedById(billId).orElseThrow(() -> ApiException.notFound("Bill"));
        List<Payment> billPayments = payments.findByBillIdInOrderByCreatedAtAsc(List.of(billId));
        List<CashierItemResponse> items =
                orderItems
                        .findByOrderSessionIdOrderByCreatedAtAsc(bill.getSession().getId())
                        .stream()
                        .map(CashierItemResponse::from)
                        .toList();
        return CashierBillResponse.from(
                bill,
                paid(billPayments),
                history(List.of(billId)).getOrDefault(billId, List.of()),
                items,
                billPayments.stream().map(CashierPaymentResponse::from).toList());
    }

    @Transactional(readOnly = true)
    @Override
    public CashierReceiptResponse receipt(UUID actorId, UUID billId) {
        User user = currentUser.require(actorId, Role.ADMIN, Role.CASHIER);
        Bill bill = bills.findDetailedById(billId).orElseThrow(() -> ApiException.notFound("Bill"));
        RestaurantSettings cfg = settings.findBySingletonKey(1).orElseGet(RestaurantSettings::new);
        List<Payment> done =
                payments.findByBillIdInOrderByCreatedAtAsc(List.of(billId)).stream()
                        .filter(p -> p.getStatus() == Payment.Status.COMPLETED)
                        .toList();
        BigDecimal paid = paid(done);
        var lines =
                orderItems
                        .findByOrderSessionIdOrderByCreatedAtAsc(bill.getSession().getId())
                        .stream()
                        .filter(
                                i ->
                                        !Set.of(
                                                        OrderItem.Status.CANCELLED,
                                                        OrderItem.Status.REJECTED,
                                                        OrderItem.Status.VOIDED)
                                                .contains(i.getStatus()))
                        .map(
                                i ->
                                        new CashierReceiptResponse.Line(
                                                i.getItemName(),
                                                i.getQuantity(),
                                                i.getUnitPrice(),
                                                Values.whole(
                                                        i.getUnitPrice()
                                                                .multiply(i.getQuantity()))))
                        .toList();
        var table = bill.getSession().getTable();
        CashierBillResponse.Discount discount =
                bill.getDiscountValue() == null
                        ? null
                        : new CashierBillResponse.Discount(
                                bill.getDiscountMode(),
                                bill.getDiscountValue(),
                                bill.getDiscountReason(),
                                bill.getDiscountAppliedBy(),
                                bill.getDiscountAppliedAt());
        var receiptPayments =
                done.stream()
                        .map(
                                p ->
                                        new CashierReceiptResponse.Payment(
                                                "P-" + p.getId(),
                                                p.getMethod().name(),
                                                p.getAmount(),
                                                p.getTendered(),
                                                p.getTendered() == null
                                                        ? null
                                                        : p.getTendered().subtract(p.getAmount()),
                                                p.getTransactionReference(),
                                                p.getPaidAt()))
                        .toList();
        return new CashierReceiptResponse(
                cfg.getName(),
                cfg.getAddress(),
                cfg.getPhone(),
                bill.getCode(),
                CashierBillResponse.statusOf(bill),
                table.getCode(),
                table.getArea().getName(),
                bill.getSession().getGuestCount(),
                bill.getSession().getWaiter().getFullName(),
                user.getFullName(),
                bill.getCreatedAt(),
                bill.getPaidAt(),
                lines,
                new CashierBillResponse.Totals(
                        bill.getSubtotal(),
                        bill.getDiscount(),
                        bill.getServiceCharge(),
                        bill.getTaxAmount(),
                        bill.getTotal()),
                discount,
                paid,
                bill.getTotal().subtract(paid).max(BigDecimal.ZERO),
                receiptPayments);
    }

    @Transactional(readOnly = true)
    @Override
    public CashierConfigResponse config(UUID actorId) {
        currentUser.require(actorId, Role.ADMIN, Role.CASHIER);
        RestaurantSettings s = settings.findBySingletonKey(1).orElseGet(RestaurantSettings::new);
        boolean transfer = s.getBankAccountNumber() != null && !s.getBankAccountNumber().isBlank();
        CashierConfigResponse.Bank bank =
                transfer
                        ? new CashierConfigResponse.Bank(
                                s.getBankName() == null ? "" : s.getBankName(),
                                s.getBankAccountNumber(),
                                s.getBankAccountName() == null ? "" : s.getBankAccountName())
                        : null;
        return new CashierConfigResponse(
                s.getName(),
                s.getAddress(),
                s.getServiceChargeRate().movePointRight(2),
                s.getVatRate().movePointRight(2),
                Map.of("CASH", true, "TRANSFER", transfer, "CARD", false),
                bank);
    }

    private static BigDecimal paid(Collection<Payment> list) {
        if (list == null) {
            return BigDecimal.ZERO;
        }
        return list.stream()
                .filter(p -> p.getStatus() == Payment.Status.COMPLETED)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<UUID, List<CashierBillResponse.Event>> history(List<UUID> billIds) {
        Map<UUID, List<CashierBillResponse.Event>> result = new java.util.HashMap<>();
        for (AuditLog a : auditLogs.findByTargetIdInOrderByCreatedAtAsc(billIds)) {
            result.computeIfAbsent(a.getTargetId(), k -> new java.util.ArrayList<>())
                    .add(
                            new CashierBillResponse.Event(
                                    a.getCreatedAt(),
                                    a.getActor() == null ? "" : a.getActor().getId().toString(),
                                    a.getDescription()));
        }
        return result;
    }
}
