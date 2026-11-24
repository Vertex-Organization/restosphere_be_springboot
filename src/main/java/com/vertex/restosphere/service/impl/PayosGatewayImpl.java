package com.vertex.restosphere.service.impl;

import com.vertex.restosphere.config.PayosProperties;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.service.PayosGateway;
import java.text.Normalizer;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import vn.payos.PayOS;
import vn.payos.core.ClientOptions;
import vn.payos.exception.PayOSException;
import vn.payos.exception.WebhookException;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.v2.paymentRequests.PaymentLinkItem;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;
import vn.payos.model.webhooks.WebhookData;

/** payOS thật qua SDK v2 ({@code paymentRequests()} và {@code webhooks()}). */
@Slf4j
public class PayosGatewayImpl implements PayosGateway {
    /** payOS chỉ nhận mô tả tối đa 25 ký tự cho tài khoản chưa liên kết. */
    static final int MAX_DESCRIPTION = 25;

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final int MAX_RETRIES = 1;

    private final PayosProperties properties;
    private final PayOS client;

    public PayosGatewayImpl(PayosProperties properties) {
        this.properties = properties;
        this.client = properties.configured() ? newClient(properties) : null;
    }

    private static PayOS newClient(PayosProperties p) {
        return new PayOS(
                ClientOptions.builder()
                        .clientId(p.clientId())
                        .apiKey(p.apiKey())
                        .checksumKey(p.checksumKey())
                        .timeoutMs((int) p.timeout().toMillis())
                        .maxRetries(MAX_RETRIES)
                        .build());
    }

    @Override
    public boolean available() {
        return client != null;
    }

    @Override
    public Link createLink(LinkRequest request) {
        var payos = requireClient();
        String description = description(request.description());
        var body =
                CreatePaymentLinkRequest.builder()
                        .orderCode(request.orderCode())
                        .amount(request.amount())
                        .description(description)
                        .returnUrl(properties.returnUrl())
                        .cancelUrl(properties.cancelUrl())
                        .expiredAt(request.expiresAt().getEpochSecond())
                        .item(
                                PaymentLinkItem.builder()
                                        .name(description)
                                        .quantity(1)
                                        .price(request.amount())
                                        .build())
                        .build();
        try {
            var created = payos.paymentRequests().create(body);
            return new Link(request.orderCode(), created.getCheckoutUrl(), created.getQrCode());
        } catch (PayOSException e) {
            throw failure("create", e);
        }
    }

    @Override
    public void cancelLink(long orderCode, String reason) {
        try {
            requireClient().paymentRequests().cancel(orderCode, reason);
        } catch (PayOSException e) {
            throw failure("cancel", e);
        }
    }

    @Override
    public LinkInfo status(long orderCode) {
        try {
            PaymentLink link = requireClient().paymentRequests().get(orderCode);
            long paid = link.getAmountPaid() == null ? 0 : link.getAmountPaid();
            return new LinkInfo(statusOf(link.getStatus()), paid);
        } catch (PayOSException e) {
            throw failure("status", e);
        }
    }

    @Override
    public PaidEvent verifyWebhook(Object body) {
        try {
            WebhookData data = requireClient().webhooks().verify(body);
            boolean success = data.getCode() == null || "00".equals(data.getCode());
            long amount = data.getAmount() == null ? 0 : data.getAmount();
            return new PaidEvent(success, data.getOrderCode(), amount, data.getReference());
        } catch (WebhookException e) {
            throw ApiException.badRequest("Chữ ký webhook không hợp lệ.");
        } catch (PayOSException e) {
            throw failure("webhook", e);
        }
    }

    static LinkStatus statusOf(PaymentLinkStatus status) {
        if (status == null) return LinkStatus.PENDING;
        return switch (status) {
            case PAID -> LinkStatus.PAID;
            case CANCELLED -> LinkStatus.CANCELLED;
            case EXPIRED -> LinkStatus.EXPIRED;
            case FAILED -> LinkStatus.FAILED;
            case PENDING, PROCESSING, UNDERPAID -> LinkStatus.PENDING;
        };
    }

    /** Bỏ dấu và cắt còn 25 ký tự theo giới hạn của payOS. */
    static String description(String text) {
        String plain =
                MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD))
                        .replaceAll("")
                        .replace('đ', 'd')
                        .replace('Đ', 'D')
                        .replaceAll("[^A-Za-z0-9 -]", "");
        return plain.length() <= MAX_DESCRIPTION ? plain : plain.substring(0, MAX_DESCRIPTION);
    }

    private PayOS requireClient() {
        if (client == null) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE, "payOS chưa được cấu hình trên máy chủ.");
        }
        return client;
    }

    // The SDK message can carry request details; log the type only and never the keys.
    private static ApiException failure(String action, PayOSException e) {
        log.warn("payOS {} failed: {}", action, e.getClass().getSimpleName());
        return new ApiException(HttpStatus.BAD_GATEWAY, "Không kết nối được payOS, thử lại sau.");
    }
}
