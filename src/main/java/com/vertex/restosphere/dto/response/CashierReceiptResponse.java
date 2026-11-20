package com.vertex.restosphere.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Everything needed to print a bill: restaurant, table, lines, money summary and successful payments. */
public record CashierReceiptResponse(String restaurantName, String address, String phone, String billCode,
        String status, String tableCode, String area, int guests, String waiter, String printedBy,
        Instant createdAt, Instant paidAt, List<Line> items, CashierBillResponse.Totals totals,
        CashierBillResponse.Discount discount, BigDecimal paid, BigDecimal remaining, List<Payment> payments) {

    public record Line(String name, BigDecimal quantity, BigDecimal unitPrice, BigDecimal lineTotal) {}

    public record Payment(String code, String method, BigDecimal amount, BigDecimal tendered, BigDecimal change,
            String bankReference, Instant at) {}
}
