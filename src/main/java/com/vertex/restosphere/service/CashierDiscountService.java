package com.vertex.restosphere.service;

import com.vertex.restosphere.dto.request.CashierDiscountRequest;
import com.vertex.restosphere.dto.response.CashierBillResponse;
import com.vertex.restosphere.dto.response.CashierRequestResponse;
import java.util.UUID;

public interface CashierDiscountService {

    CashierBillResponse apply(UUID actorId, UUID billId, CashierDiscountRequest req);

    CashierBillResponse remove(UUID actorId, UUID billId, long billVersion);

    CashierRequestResponse request(UUID actorId, UUID billId, CashierDiscountRequest req);
}
