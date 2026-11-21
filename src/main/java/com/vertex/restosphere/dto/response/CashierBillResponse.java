package com.vertex.restosphere.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vertex.restosphere.entity.Bill;
import com.vertex.restosphere.entity.TableSession;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CashierBillResponse(
        UUID id,
        String code,
        UUID sessionId,
        String status,
        long version,
        Instant createdAt,
        Instant requestedAt,
        Instant paidAt,
        BigDecimal amount,
        Discount discount,
        Totals totals,
        BigDecimal paid,
        BigDecimal remaining,
        String tableCode,
        String area,
        int guests,
        List<Event> history,
        List<CashierItemResponse> items,
        List<CashierPaymentResponse> payments) {
    public record Discount(
            String mode, BigDecimal value, String reason, String appliedBy, Instant appliedAt) {}

    public record Totals(
            BigDecimal subtotal,
            BigDecimal discount,
            BigDecimal serviceCharge,
            BigDecimal tax,
            BigDecimal total) {}

    public record Event(Instant at, String by, String text) {}

    public static String statusOf(Bill b) {
        if (b.getStatus() == Bill.Status.PAID) {
            return "PAID";
        }
        return b.getSession().getStatus() == TableSession.Status.PAYMENT_REQUESTED
                ? "PAYMENT_REQUESTED"
                : "UNPAID";
    }

    public static CashierBillResponse from(
            Bill b,
            BigDecimal paid,
            List<Event> history,
            List<CashierItemResponse> items,
            List<CashierPaymentResponse> payments) {
        Discount discount =
                b.getDiscountValue() == null
                        ? null
                        : new Discount(
                                b.getDiscountMode(),
                                b.getDiscountValue(),
                                b.getDiscountReason(),
                                b.getDiscountAppliedBy(),
                                b.getDiscountAppliedAt());
        var table = b.getSession().getTable();
        return new CashierBillResponse(
                b.getId(),
                b.getCode(),
                b.getSession().getId(),
                statusOf(b),
                b.getVersion(),
                b.getCreatedAt(),
                b.getRequestedAt(),
                b.getPaidAt(),
                b.getTotal(),
                discount,
                new Totals(
                        b.getSubtotal(),
                        b.getDiscount(),
                        b.getServiceCharge(),
                        b.getTaxAmount(),
                        b.getTotal()),
                paid,
                b.getTotal().subtract(paid).max(BigDecimal.ZERO),
                table.getCode(),
                table.getArea().getName(),
                b.getSession().getGuestCount(),
                history,
                items,
                payments);
    }
}
