package com.vertex.restosphere.mapper;

import static com.vertex.restosphere.common.util.Values.id;
import static com.vertex.restosphere.common.util.Values.map;

import com.vertex.restosphere.entity.AuditLog;
import com.vertex.restosphere.entity.Bill;
import com.vertex.restosphere.entity.Payment;
import com.vertex.restosphere.entity.TableSession;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Hóa đơn và thanh toán dạng JSON cho các workspace. */
@Component
public class BillMapper {

    public Map<String, Object> toMap(Bill b, List<AuditLog> history) {
        return map(
                "id",
                id(b),
                "code",
                b.getCode(),
                "sessionId",
                id(b.getSession()),
                "status",
                status(b),
                "version",
                b.getVersion(),
                "createdAt",
                b.getCreatedAt(),
                "requestedAt",
                b.getRequestedAt(),
                "paidAt",
                b.getPaidAt(),
                "amount",
                b.getTotal(),
                "currentAmount",
                b.getTotal(),
                "requestedAmount",
                b.getRequestedAt() == null ? null : b.getTotal(),
                "discount",
                discount(b),
                "totals",
                map(
                        "subtotal",
                        b.getSubtotal(),
                        "discount",
                        b.getDiscount(),
                        "serviceCharge",
                        b.getServiceCharge(),
                        "tax",
                        b.getTaxAmount(),
                        "total",
                        b.getTotal()),
                "history",
                history.stream()
                        .map(
                                a ->
                                        map(
                                                "at",
                                                a.getCreatedAt(),
                                                "by",
                                                id(a.getActor()),
                                                "text",
                                                a.getDescription()))
                        .toList());
    }

    public Map<String, Object> toMap(Payment p) {
        return map(
                "id",
                id(p),
                "code",
                "P-" + id(p),
                "billId",
                id(p.getBill()),
                "shiftId",
                id(p.getCashierShift()),
                "method",
                p.getMethod(),
                "status",
                p.getStatus() == Payment.Status.COMPLETED ? "SUCCEEDED" : p.getStatus().name(),
                "amount",
                p.getAmount(),
                "tendered",
                p.getTendered(),
                "change",
                p.getTendered() == null ? null : p.getTendered().subtract(p.getAmount()),
                "transferContent",
                p.getMethod() == Payment.Method.TRANSFER ? "RS" + id(p).replace("-", "") : null,
                "bankReference",
                p.getTransactionReference(),
                "verifiedBy",
                p.getVerifiedBy(),
                "createdBy",
                p.getCreatedBy(),
                "createdAt",
                p.getCreatedAt(),
                "completedAt",
                p.getPaidAt());
    }

    private static String status(Bill b) {
        if (b.getStatus() == Bill.Status.PAID) {
            return "PAID";
        }
        return b.getSession().getStatus() == TableSession.Status.PAYMENT_REQUESTED
                ? "PAYMENT_REQUESTED"
                : "UNPAID";
    }

    private static Map<String, Object> discount(Bill b) {
        if (b.getDiscountValue() == null) {
            return null;
        }
        return map(
                "mode",
                b.getDiscountMode(),
                "value",
                b.getDiscountValue(),
                "reason",
                b.getDiscountReason(),
                "appliedBy",
                b.getDiscountAppliedBy(),
                "appliedAt",
                b.getDiscountAppliedAt());
    }
}
