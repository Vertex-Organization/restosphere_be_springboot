package com.vertex.restosphere.service;

import com.vertex.restosphere.dto.response.CashierPaymentResponse;
import com.vertex.restosphere.dto.response.PaymentStatusResponse;
import java.util.UUID;

/** Tra cứu và "Kiểm tra lại" các payment payOS cho nhân viên và cho trang kết quả của khách. */
public interface PayosPaymentQueries {

    /** Hỏi payOS trạng thái đơn của một payment PENDING và cập nhật như webhook. */
    CashierPaymentResponse refresh(UUID actorId, UUID paymentId);

    /**
     * Trạng thái cho khách theo mã đơn payOS kèm mã đặt bàn và số điện thoại (khách không đăng
     * nhập). Cũng hỏi payOS nếu payment còn PENDING, phòng mất webhook.
     */
    PaymentStatusResponse publicStatus(long orderCode, String reservationCode, String phone);
}
