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
