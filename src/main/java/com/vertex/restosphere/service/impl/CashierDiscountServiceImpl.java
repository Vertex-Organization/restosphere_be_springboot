package com.vertex.restosphere.service.impl;

import com.vertex.restosphere.common.enums.DiscountMode;
import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.common.util.Values;
import com.vertex.restosphere.dto.request.CashierDiscountRequest;
import com.vertex.restosphere.dto.response.CashierBillResponse;
import com.vertex.restosphere.dto.response.CashierRequestResponse;
import com.vertex.restosphere.entity.ApprovalRequest;
import com.vertex.restosphere.entity.Bill;
import com.vertex.restosphere.entity.Employee;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.BillingWorkflow;
import com.vertex.restosphere.service.CashierBillService;
import com.vertex.restosphere.service.CashierDiscountService;
import com.vertex.restosphere.service.RestaurantLock;
import com.vertex.restosphere.service.WorkspaceCommit;
import com.vertex.restosphere.service.WorkspaceStore;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CashierDiscountServiceImpl implements CashierDiscountService {
    private record Parsed(DiscountMode mode, BigDecimal value, BigDecimal amount, String reason) {}

    private final CurrentUserService currentUser;
    private final RestaurantLock restaurantLock;
    private final WorkspaceStore store;
    private final BillingWorkflow billing;
    private final WorkspaceCommit writes;
    private final CashierBillService billViews;

    @Transactional
    @Override
    public CashierBillResponse apply(UUID actorId, UUID billId, CashierDiscountRequest req) {
        Employee user = currentUser.require(actorId, Role.ADMIN, Role.CASHIER);
        restaurantLock.acquire();
        Bill bill = prepare(billId, req.billVersion());
        Parsed d = parse(bill, req.discount());
        if (d.amount()
                        .compareTo(
                                Values.whole(
                                        bill.getSubtotal()
                                                .multiply(
                                                        BigDecimal.valueOf(
                                                                store.lock()
                                                                        .getCashierDiscountPercent()))
                                                .movePointLeft(2)))
                > 0) {
            throw ApiException.conflict("Discount requires manager approval");
        }
        billing.applyDiscount(bill, d.mode(), d.value(), d.reason(), user);
        writes.commit(user, "apply-discount");
        store.flush();
        return billViews.view(billId);
    }

    @Transactional
    @Override
    public CashierBillResponse remove(UUID actorId, UUID billId, long billVersion) {
        Employee user = currentUser.require(actorId, Role.ADMIN, Role.CASHIER);
        restaurantLock.acquire();
        Bill bill = prepare(billId, billVersion);
        bill.setDiscountValue(null);
        bill.setDiscountMode(null);
        bill.setApprovalRequest(null);
        billing.recalculate(bill);
        writes.commit(user, "remove-discount");
        store.flush();
        return billViews.view(billId);
    }

    @Transactional
    @Override
    public CashierRequestResponse request(UUID actorId, UUID billId, CashierDiscountRequest req) {
        Employee user = currentUser.require(actorId, Role.ADMIN, Role.CASHIER);
        restaurantLock.acquire();
        Bill bill = prepare(billId, req.billVersion());
        Parsed d = parse(bill, req.discount());
        ApprovalRequest r =
                billing.request(
                        ApprovalRequest.Type.DISCOUNT,
                        "BILL",
                        bill.getId(),
                        d.amount(),
                        d.reason(),
                        user);
        r.setDiscountMode(d.mode());
        r.setDiscountValue(d.value());
        writes.commit(user, "request-discount");
        store.flush();
        return CashierRequestResponse.from(r, bill.getId(), null, null);
    }

    private Bill prepare(UUID billId, long billVersion) {
        Bill bill = store.get(Bill.class, billId.toString());
        billing.requireVersion(bill, billVersion);
        billing.recalculate(bill);
        billing.editable(bill);
        return bill;
    }

    private Parsed parse(Bill bill, CashierDiscountRequest.Discount d) {
        DiscountMode mode = d.mode();
        if (mode == null) {
            throw Values.invalid("mode is required");
        }
        if (d.value() == null) {
            throw Values.invalid("Invalid value");
        }
        BigDecimal value = Values.money(d.value(), "value");
        if (value.signum() == 0
                || (mode == DiscountMode.PERCENT && value.compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw Values.invalid("Invalid discount");
        }
        BigDecimal amount =
                mode == DiscountMode.PERCENT
                        ? Values.whole(bill.getSubtotal().multiply(value).movePointLeft(2))
                        : value;
        if (amount.compareTo(bill.getSubtotal()) > 0) {
            throw Values.invalid("Discount exceeds subtotal");
        }
        String reason = d.reason() == null ? "" : d.reason().trim();
        if (reason.isEmpty() || reason.length() > 1000) {
            throw Values.invalid("reason is required (max 1000)");
        }
        return new Parsed(mode, value, amount, reason);
    }
}
