package com.vertex.restosphere.dto.response;

import java.math.BigDecimal;
import java.util.Map;

/** Matches the FE BillingConfig type; rates are basis points (1% = 100). */
public record CashierConfigResponse(String restaurantName, String address, BigDecimal serviceChargeRate,
        BigDecimal taxRate, Map<String, Boolean> methods, Bank bank) {

    public record Bank(String bankName, String accountNumber, String accountName) {}
}
