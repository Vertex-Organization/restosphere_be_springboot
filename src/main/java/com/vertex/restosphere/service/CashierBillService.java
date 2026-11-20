package com.vertex.restosphere.service;

import com.vertex.restosphere.dto.response.CashierBillResponse;
import com.vertex.restosphere.dto.response.CashierConfigResponse;
import com.vertex.restosphere.dto.response.CashierReceiptResponse;
import java.util.List;
import java.util.UUID;

public interface CashierBillService {

    List<CashierBillResponse> list(UUID actorId, String status);

    CashierBillResponse detail(UUID actorId, UUID billId);

    CashierBillResponse view(UUID billId);

    CashierReceiptResponse receipt(UUID actorId, UUID billId);

    CashierConfigResponse config(UUID actorId);
}
