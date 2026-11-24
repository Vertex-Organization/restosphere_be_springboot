package com.vertex.restosphere.service;

import java.time.Instant;

/** Cổng gọi payOS (SDK v2). Chỉ tạo/hủy/hỏi link và xác thực webhook; không chứa nghiệp vụ. */
public interface PayosGateway {

    enum LinkStatus {
        PENDING,
        PAID,
        CANCELLED,
        EXPIRED,
        FAILED
    }

    record LinkRequest(long orderCode, long amount, String description, Instant expiresAt) {}

    record Link(long orderCode, String checkoutUrl, String qrCode) {}

    record LinkInfo(LinkStatus status, long amountPaid) {}

    /** Dữ liệu webhook đã xác thực chữ ký. */
    record PaidEvent(boolean success, long orderCode, long amount, String reference) {}

    /** False khi chưa có đủ khóa payOS: mọi lệnh tạo link sẽ báo lỗi 503. */
    boolean available();

    Link createLink(LinkRequest request);

    void cancelLink(long orderCode, String reason);

    LinkInfo status(long orderCode);

    /** Ném {@code ApiException} 400 khi chữ ký sai. */
    PaidEvent verifyWebhook(Object body);
}
