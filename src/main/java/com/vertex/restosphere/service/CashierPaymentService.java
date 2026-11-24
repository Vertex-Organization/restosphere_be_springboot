package com.vertex.restosphere.service;

import com.vertex.restosphere.dto.request.CashierDepositCashRequest;
import com.vertex.restosphere.dto.request.CashierDepositTransferRequest;
import com.vertex.restosphere.dto.request.CashierManualTransferRequest;
import com.vertex.restosphere.dto.request.CashierPayCashRequest;
import com.vertex.restosphere.dto.request.CashierPayosLinkRequest;
import com.vertex.restosphere.dto.response.CashierPaymentResponse;
import com.vertex.restosphere.dto.response.CashierPendingDepositResponse;
import com.vertex.restosphere.entity.Payment;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CashierPaymentService {

    record Outcome(CashierPaymentResponse payment, boolean created) {}

    List<CashierPaymentResponse> history(
            UUID actorId, Instant from, Instant to, Payment.Method method, String status);

    Outcome payCash(UUID actorId, UUID billId, CashierPayCashRequest req);

    /** Tạo link payOS cho phần còn lại của bill (QR in trên bill); hủy link cũ khi số tiền đổi. */
    Outcome createPayosLink(UUID actorId, UUID billId, CashierPayosLinkRequest req);

    /** Thu thủ công khi payOS lỗi: tạo thẳng COMPLETED, có quản lý xác nhận. */
    Outcome payManualTransfer(UUID actorId, UUID billId, CashierManualTransferRequest req);

    /** Danh sách "Cọc chờ thu": đặt bàn lễ tân đã chuyển sang thu ngân thu cọc tiền mặt. */
    List<CashierPendingDepositResponse> pendingDeposits(UUID actorId);

    /** Thu cọc tiền mặt vào ca của thu ngân; không hoàn cọc dù hủy hay không đến. */
    Outcome depositCash(UUID actorId, UUID reservationId, CashierDepositCashRequest req);

    /** Thu cọc chuyển khoản thủ công khi payOS lỗi; quản lý xác nhận ngay tại quầy. */
    Outcome depositManualTransfer(
            UUID actorId, UUID reservationId, CashierDepositTransferRequest req);
}
