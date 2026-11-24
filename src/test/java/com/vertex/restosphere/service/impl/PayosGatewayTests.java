package com.vertex.restosphere.service.impl;

import static org.junit.jupiter.api.Assertions.*;

import com.vertex.restosphere.config.PayosProperties;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.service.PayosGateway;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;

class PayosGatewayTests {

    static PayosProperties props(String clientId, String apiKey, String checksumKey) {
        return new PayosProperties(
                false,
                clientId,
                apiKey,
                checksumKey,
                "http://localhost/return",
                "http://localhost/cancel",
                Duration.ofMinutes(30),
                Duration.ofSeconds(5));
    }

    @Test
    void descriptionIsAsciiAndAtMost25Characters() {
        assertEquals("Thanh toan ban A01", PayosGatewayImpl.description("Thanh toán bàn A01"));
        assertEquals(
                "Dat coc RS-241125-001", PayosGatewayImpl.description("Đặt cọc RS-241125-001"));
        String longText = "Thanh toan hoa don B-ABCDEF cho ban rat dai";
        assertEquals(25, PayosGatewayImpl.description(longText).length());
        assertTrue(PayosGatewayImpl.description("Cọc #1 (VIP)!").matches("[A-Za-z0-9 -]*"));
    }

    @Test
    void placeholderOrBlankKeysMeanNotConfigured() {
        assertFalse(props("not-configured", "not-configured", "not-configured").configured());
        assertFalse(props("", "k", "c").configured());
        assertFalse(props(null, "k", "c").configured());
        assertTrue(props("id", "k", "c").configured());
    }

    @Test
    void withoutKeysEveryCallFailsWith503AndNothingIsSent() {
        var gateway = new PayosGatewayImpl(props("not-configured", "x", "y"));
        assertFalse(gateway.available());
        var request = new PayosGateway.LinkRequest(1, 1000, "Test", Instant.now().plusSeconds(600));
        for (Runnable call :
                new Runnable[] {
                    () -> gateway.createLink(request),
                    () -> gateway.cancelLink(1, "x"),
                    () -> gateway.status(1),
                    () -> gateway.verifyWebhook("{}")
                }) {
            var e = assertThrows(ApiException.class, call::run);
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatus());
        }
    }

    @Test
    void sdkStatusesMapToTheStatesTheBusinessUnderstands() {
        assertEquals(
                PayosGateway.LinkStatus.PAID, PayosGatewayImpl.statusOf(PaymentLinkStatus.PAID));
        assertEquals(
                PayosGateway.LinkStatus.CANCELLED,
                PayosGatewayImpl.statusOf(PaymentLinkStatus.CANCELLED));
        assertEquals(
                PayosGateway.LinkStatus.EXPIRED,
                PayosGatewayImpl.statusOf(PaymentLinkStatus.EXPIRED));
        for (var pending :
                new PaymentLinkStatus[] {
                    PaymentLinkStatus.PENDING,
                    PaymentLinkStatus.PROCESSING,
                    PaymentLinkStatus.UNDERPAID
                }) {
            assertEquals(PayosGateway.LinkStatus.PENDING, PayosGatewayImpl.statusOf(pending));
        }
    }

    // Signs a webhook the way payOS does: HMAC over the data object, with the SDK's own crypto.
    private static java.util.Map<String, Object> signedWebhook(String checksumKey, long amount) {
        var data =
                vn.payos.model.webhooks.WebhookData.builder()
                        .orderCode(123L)
                        .amount(amount)
                        .description("VQRIO123")
                        .accountNumber("12345678")
                        .reference("TF230204212323")
                        .transactionDateTime("2023-02-04 18:25:00")
                        .currency("VND")
                        .paymentLinkId("124c33293c43417ab7879e14c8d9eb18")
                        .code("00")
                        .desc("Thành công")
                        .build();
        String signature =
                new vn.payos.crypto.CryptoProviderImpl().createSignatureFromObj(data, checksumKey);
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> dataMap = mapper.convertValue(data, java.util.Map.class);
        return java.util.Map.of(
                "code",
                "00",
                "desc",
                "success",
                "success",
                true,
                "data",
                dataMap,
                "signature",
                signature);
    }

    @Test
    void webhookWithAValidSignatureIsAcceptedAndATamperedOneIsRejectedWith400() {
        var gateway = new PayosGatewayImpl(props("id", "api", "checksum-secret"));
        var body = signedWebhook("checksum-secret", 50000);

        var event = gateway.verifyWebhook(body);
        assertTrue(event.success());
        assertEquals(123L, event.orderCode());
        assertEquals(50000L, event.amount());

        var tampered = new java.util.HashMap<>(body);
        tampered.put("data", signedWebhook("checksum-secret", 50000).get("data"));
        @SuppressWarnings("unchecked")
        var data =
                new java.util.LinkedHashMap<>((java.util.Map<String, Object>) tampered.get("data"));
        data.put("amount", 1L);
        tampered.put("data", data);
        var e = assertThrows(ApiException.class, () -> gateway.verifyWebhook(tampered));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());

        var wrongKey = signedWebhook("another-key", 50000);
        assertEquals(
                HttpStatus.BAD_REQUEST,
                assertThrows(ApiException.class, () -> gateway.verifyWebhook(wrongKey))
                        .getStatus());
    }

    @Test
    void fakeGatewayRemembersLinksAndNeverUnpaysAPaidOne() {
        var fake = new FakePayosGateway();
        fake.createLink(new PayosGateway.LinkRequest(7, 5000, "Test", Instant.now()));
        assertEquals(PayosGateway.LinkStatus.PENDING, fake.status(7).status());
        fake.markPaid(7);
        fake.cancelLink(7, "late");
        assertEquals(PayosGateway.LinkStatus.PAID, fake.status(7).status());
        assertEquals(5000, fake.status(7).amountPaid());
    }
}
