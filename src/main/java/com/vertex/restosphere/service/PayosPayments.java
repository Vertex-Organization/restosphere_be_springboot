package com.vertex.restosphere.service;

import com.vertex.restosphere.entity.Payment;

/**
 * Điểm vào duy nhất khi payOS báo một đơn đã trả: webhook, "Kiểm tra lại" và job hết hạn đều đi qua
 * đây, nên cùng một luật và idempotent.
 */
public interface PayosPayments {

    enum Outcome {
        /** Payment chuyển sang COMPLETED lần này. */
        COMPLETED,
        /** Đã COMPLETED từ trước, không làm gì. */
        ALREADY_COMPLETED,
        /** Không có payment nào mang mã đơn này (dữ liệu thử của payOS). */
        UNKNOWN,
        /** Tiền về cho payment đã hủy: không tự khôi phục, báo quản lý xử lý tay. */
        LATE_PAYMENT,
        /** Số tiền không khớp payment: bỏ qua và báo quản lý. */
        AMOUNT_MISMATCH,
        /** payOS chưa ghi nhận thanh toán. */
        STILL_PENDING,
        /** payOS đã hủy hoặc hết hạn link; payment được đóng lại, khách không trả được nữa. */
        CLOSED
    }

    /**
     * Xác thực chữ ký rồi xử lý như {@link #markPaid}; chữ ký sai -> 400, webhook thất bại bị bỏ
     * qua.
     */
    Outcome handleWebhook(Object body);

    /** Chỉ cho dev: giả lập khách trả đủ link đã tạo, đi qua đúng {@link #markPaid} của webhook. */
    Outcome simulatePaid(long orderCode);

    /** Ghi nhận payOS đã nhận {@code amount} cho {@code orderCode}. */
    Outcome markPaid(long orderCode, long amount, String reference);

    /** Hỏi payOS trạng thái đơn của payment và cập nhật như webhook. */
    Outcome refresh(Payment payment);
}
