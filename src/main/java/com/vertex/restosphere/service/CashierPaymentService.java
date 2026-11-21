package com.vertex.restosphere.service;

import com.vertex.restosphere.dto.request.CashierConfirmTransferRequest;
import com.vertex.restosphere.dto.request.CashierCreateTransferRequest;
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

    Outcome createTransfer(UUID actorId, UUID billId, CashierCreateTransferRequest req);

    CashierPaymentResponse confirmTransfer(
            UUID actorId, UUID paymentId, CashierConfirmTransferRequest req);

    CashierPaymentResponse cancelTransfer(UUID actorId, UUID paymentId);
}
