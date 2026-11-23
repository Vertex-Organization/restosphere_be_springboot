package com.vertex.restosphere.service;

import com.vertex.restosphere.dto.request.CashierManualTransferRequest;
import com.vertex.restosphere.dto.request.CashierPayCashRequest;
import com.vertex.restosphere.dto.response.CashierPaymentResponse;
import com.vertex.restosphere.entity.Payment;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CashierPaymentService {

    record Outcome(CashierPaymentResponse payment, boolean created) {}

    List<CashierPaymentResponse> history(
            UUID actorId, Instant from, Instant to, Payment.Method method, String status);

    Outcome payCash(UUID actorId, UUID billId, CashierPayCashRequest req);

    /** Thu thủ công khi payOS lỗi: tạo thẳng COMPLETED, có quản lý xác nhận. */
    Outcome payManualTransfer(UUID actorId, UUID billId, CashierManualTransferRequest req);
}
