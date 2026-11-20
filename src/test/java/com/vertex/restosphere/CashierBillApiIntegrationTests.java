package com.vertex.restosphere;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vertex.restosphere.entity.RestaurantSettings;
import com.vertex.restosphere.entity.TableSession;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class CashierBillApiIntegrationTests extends CashierApiTestBase {
    @Override
    String prefix() {
        return "c2";
    }

    @Test
    void authIsEnforced() throws Exception {
        for (String p : List.of("/bills", "/bills/" + UUID.randomUUID(), "/config")) {
            call(HttpMethod.GET, p, null, null).andExpect(status().isUnauthorized());
            call(HttpMethod.GET, p, waiterToken, null).andExpect(status().isForbidden());
        }
    }

    @Test
    void listFiltersByDerivedStatusAndRejectsUnknownStatus() throws Exception {
        Fixture f = billFixture();
        String all = ok(call(HttpMethod.GET, "/bills", cashierToken, null));
        assertThat(this.<List<Object>>json(all, "$[?(@.id=='%s')]".formatted(f.bill().getId()))).hasSize(1);
        assertThat(this.<List<String>>json(all, "$[?(@.id=='%s')].status".formatted(f.bill().getId())))
                .containsExactly("PAYMENT_REQUESTED");

        String requested = ok(call(HttpMethod.GET, "/bills?status=PAYMENT_REQUESTED", cashierToken, null));
        assertThat(this.<List<Object>>json(requested, "$[?(@.id=='%s')]".formatted(f.bill().getId()))).hasSize(1);
        String paid = ok(call(HttpMethod.GET, "/bills?status=PAID", cashierToken, null));
        assertThat(this.<List<Object>>json(paid, "$[?(@.id=='%s')]".formatted(f.bill().getId()))).isEmpty();
        String unpaid = ok(call(HttpMethod.GET, "/bills?status=UNPAID", cashierToken, null));
        assertThat(this.<List<Object>>json(unpaid, "$[?(@.id=='%s')]".formatted(f.bill().getId()))).isEmpty();
        call(HttpMethod.GET, "/bills?status=BOGUS", cashierToken, null).andExpect(status().isBadRequest());

        assertThat(this.<List<Object>>json(all, "$[?(@.id=='%s' && @.items)]".formatted(f.bill().getId())))
                .isEmpty();
    }

    @Test
    void detailReturnsItemsTotalsPaidAndRemaining() throws Exception {
        Fixture f = billFixture();
        long before = revision();
        String body = ok(call(HttpMethod.GET, "/bills/" + f.bill().getId(), cashierToken, null));
        assertThat(revision()).isEqualTo(before);
        assertThat(this.<String>json(body, "$.id")).isEqualTo(f.bill().getId().toString());
        assertThat(this.<String>json(body, "$.status")).isEqualTo("PAYMENT_REQUESTED");
        assertThat(this.<Number>json(body, "$.version").longValue()).isEqualTo(f.bill().getVersion());
        assertThat(this.<Number>json(body, "$.totals.subtotal").longValue()).isEqualTo(100000L);
        assertThat(this.<Number>json(body, "$.totals.total").longValue()).isEqualTo(100000L);
        assertThat(this.<Number>json(body, "$.paid").longValue()).isZero();
        assertThat(this.<Number>json(body, "$.remaining").longValue()).isEqualTo(100000L);
        assertThat(this.<String>json(body, "$.tableCode")).isEqualTo(f.table().getCode());
        assertThat(this.<Integer>json(body, "$.guests")).isEqualTo(2);
        assertThat(this.<List<Object>>json(body, "$.items")).hasSize(1);
        assertThat(this.<String>json(body, "$.items[0].name")).isEqualTo("Dish");
        assertThat(this.<String>json(body, "$.items[0].status")).isEqualTo("SERVED");
        assertThat(this.<List<Object>>json(body, "$.payments")).isEmpty();
    }

    @Test
    void detailStatusFollowsSessionAndUnknownBillIs404() throws Exception {
        Fixture f = billFixture();
        TableSession session = f.session();
        session.setStatus(TableSession.Status.OPEN);
        persist(session);
        String body = ok(call(HttpMethod.GET, "/bills/" + f.bill().getId(), cashierToken, null));
        assertThat(this.<String>json(body, "$.status")).isEqualTo("UNPAID");
        call(HttpMethod.GET, "/bills/" + UUID.randomUUID(), cashierToken, null).andExpect(status().isNotFound());
        call(HttpMethod.GET, "/bills/not-a-uuid", cashierToken, null).andExpect(status().isBadRequest());
    }

    @Test
    void configExposesRatesInBasisPointsAndBank() throws Exception {
        RestaurantSettings s = settings.findBySingletonKey(1).orElseGet(() -> {
            RestaurantSettings created = new RestaurantSettings();
            created.setName("RestoSphere");
            return settings.save(created);
        });
        String old = s.getBankAccountNumber();
        BigDecimal vat = s.getVatRate();
        BigDecimal service = s.getServiceChargeRate();
        try {
            tx.executeWithoutResult(t -> {
                RestaurantSettings x = settings.findBySingletonKey(1).orElseThrow();
                x.setVatRate(new BigDecimal("8"));
                x.setServiceChargeRate(new BigDecimal("5"));
                x.setBankAccountNumber("123456");
                x.setBankName("VCB");
                x.setBankAccountName("RESTO");
            });
            String body = ok(call(HttpMethod.GET, "/config", cashierToken, null));
            assertThat(this.<Number>json(body, "$.taxRate").intValue()).isEqualTo(800);
            assertThat(this.<Number>json(body, "$.serviceChargeRate").intValue()).isEqualTo(500);
            assertThat(this.<Boolean>json(body, "$.methods.TRANSFER")).isTrue();
            assertThat(this.<Boolean>json(body, "$.methods.CASH")).isTrue();
            assertThat(this.<String>json(body, "$.bank.accountNumber")).isEqualTo("123456");
        } finally {
            tx.executeWithoutResult(t -> {
                RestaurantSettings x = settings.findBySingletonKey(1).orElseThrow();
                x.setVatRate(vat);
                x.setServiceChargeRate(service);
                x.setBankAccountNumber(old);
            });
        }
    }
}
