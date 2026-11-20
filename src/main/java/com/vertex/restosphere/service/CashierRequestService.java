package com.vertex.restosphere.service;

import com.vertex.restosphere.dto.request.CashierAdjustmentRequestRequest;
import com.vertex.restosphere.dto.request.CashierReasonRequest;
import com.vertex.restosphere.dto.request.CashierReturnPaymentRequest;
import com.vertex.restosphere.dto.response.CashierBillResponse;
import com.vertex.restosphere.dto.response.CashierRequestResponse;
import java.util.List;
import java.util.UUID;

public interface CashierRequestService {

    List<CashierRequestResponse> list(UUID actorId);

    CashierRequestResponse requestAdjustment(UUID actorId, UUID billId, CashierAdjustmentRequestRequest req);

    CashierRequestResponse requestVoid(UUID actorId, UUID paymentId, CashierReasonRequest req);

    CashierRequestResponse withdraw(UUID actorId, UUID requestId);

    CashierBillResponse returnPaymentRequest(UUID actorId, UUID billId, CashierReturnPaymentRequest req);
}
