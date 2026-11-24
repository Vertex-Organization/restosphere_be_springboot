package com.vertex.restosphere.service.impl;

import com.vertex.restosphere.service.PayosGateway;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * payOS giả cho dev/test: không gọi mạng. Link nhớ trạng thái trong bộ nhớ; "đã trả" chỉ đặt qua
 * {@link #markPaid}, do endpoint dev hoặc test gọi.
 */
public class FakePayosGateway implements PayosGateway {
    private final Map<Long, LinkInfo> links = new ConcurrentHashMap<>();
    private final Map<Long, Long> amounts = new ConcurrentHashMap<>();

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public Link createLink(LinkRequest request) {
        links.put(request.orderCode(), new LinkInfo(LinkStatus.PENDING, 0));
        amounts.put(request.orderCode(), request.amount());
        String url = "https://pay.payos.vn/fake/" + request.orderCode();
        return new Link(request.orderCode(), url, "FAKE-QR-" + request.orderCode());
    }

    @Override
    public void cancelLink(long orderCode, String reason) {
        links.computeIfPresent(
                orderCode,
                (k, v) ->
                        v.status() == LinkStatus.PAID ? v : new LinkInfo(LinkStatus.CANCELLED, 0));
    }

    @Override
    public LinkInfo status(long orderCode) {
        return links.getOrDefault(orderCode, new LinkInfo(LinkStatus.PENDING, 0));
    }

    /** Chữ ký không được kiểm tra: webhook giả luôn đi qua endpoint dev, không qua payOS. */
    @Override
    public PaidEvent verifyWebhook(Object body) {
        throw new UnsupportedOperationException("Fake gateway has no signed webhooks");
    }

    /** Đánh dấu link đã trả đủ số tiền đã tạo, như payOS khi khách quét QR. */
    public void markPaid(long orderCode) {
        long amount = amounts.getOrDefault(orderCode, 0L);
        links.put(orderCode, new LinkInfo(LinkStatus.PAID, amount));
    }
}
