package com.vertex.restosphere.dto.request;

/** Ask a manager to remove one line from a bill. Fields are validated in the service (legacy order). */
public record CashierAdjustmentRequestRequest(String itemId, String reason) {}
