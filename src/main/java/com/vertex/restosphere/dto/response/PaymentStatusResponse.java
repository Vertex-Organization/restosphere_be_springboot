package com.vertex.restosphere.dto.response;

import com.vertex.restosphere.entity.Payment;

/**
 * Trạng thái thanh toán trả cho trang kết quả: lấy từ máy chủ, không tin query string của payOS.
 */
public record PaymentStatusResponse(String status, boolean paid, String reservationStatus) {
    public static PaymentStatusResponse from(Payment p) {
        boolean paid = p.getStatus() == Payment.Status.COMPLETED;
        String reservation =
                p.getReservation() == null ? null : p.getReservation().getStatus().name();
        return new PaymentStatusResponse(
                paid ? "SUCCEEDED" : p.getStatus().name(), paid, reservation);
    }
}
