package com.vertex.restosphere;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vertex.restosphere.entity.Bill;
import com.vertex.restosphere.entity.RestaurantSettings;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class CashierTransactionReceiptApiIntegrationTests extends CashierApiTestBase {

    @Override
    String prefix() {
        return "c7";
    }

    private long version(Fixture f) {
        return tx.execute(s -> em.find(Bill.class, f.bill().getId()).getVersion());
    }

    private String pay(Fixture f, long amount, String key) throws Exception {
        String body = created(call(HttpMethod.POST, "/bills/" + f.bill().getId() + "/payments/cash", cashierToken,
                "{\"billVersion\":%d,\"amount\":%d,\"tendered\":%d,\"idempotencyKey\":\"%s\"}".formatted(version(f),
                        amount, amount + 5000, key)));
        return this.<String>json(body, "$.id");
    }

    private void ensureSettings() {
        tx.executeWithoutResult(t -> {
            RestaurantSettings s = settings.findBySingletonKey(1).orElseGet(() -> {
                RestaurantSettings created = new RestaurantSettings();
                created.setName("RestoSphere");
                return settings.save(created);
            });
            s.setBankAccountNumber("0123456789");
        });
    }

    @Test
    void authIsEnforced() throws Exception {
        Fixture f = billFixture();
        for (String p : List.of("/payments", "/bills/" + f.bill().getId() + "/receipt")) {
            call(HttpMethod.GET, p, null, null).andExpect(status().isUnauthorized());
            call(HttpMethod.GET, p, waiterToken, null).andExpect(status().isForbidden());
        }
    }

    @Test
    void historyFiltersByMethodStatusAndTimeWindow() throws Exception {
        ensureSettings();
        Fixture f = billFixture();
        Fixture g = billFixture();
        openShift(cashierToken, 0);
        String cash = pay(f, 30000, "c7-cash");
        String transfer = this.<String>json(created(call(HttpMethod.POST,
                "/bills/" + g.bill().getId() + "/payments/transfer", cashierToken,
                "{\"billVersion\":%d,\"amount\":50000,\"idempotencyKey\":\"c7-tr\"}".formatted(version(g)))),
                "$.id");

        String all = ok(call(HttpMethod.GET, "/payments", cashierToken, null));
        assertThat(this.<List<String>>json(all, "$[*].id")).contains(cash, transfer);

        String cashOnly = ok(call(HttpMethod.GET, "/payments?method=CASH", cashierToken, null));
        assertThat(this.<List<String>>json(cashOnly, "$[*].id")).contains(cash).doesNotContain(transfer);
        String pending = ok(call(HttpMethod.GET, "/payments?status=PENDING", cashierToken, null));
        assertThat(this.<List<String>>json(pending, "$[*].id")).contains(transfer).doesNotContain(cash);
        String succeeded = ok(call(HttpMethod.GET, "/payments?status=SUCCEEDED&method=CASH", cashierToken, null));
        assertThat(this.<List<String>>json(succeeded, "$[*].id")).contains(cash).doesNotContain(transfer);
        String none = ok(call(HttpMethod.GET, "/payments?status=SUCCEEDED&method=TRANSFER", cashierToken, null));
        assertThat(this.<List<String>>json(none, "$[?(@.id=='%s')]".formatted(transfer))).isEmpty();

        String from = Instant.now().minus(1, ChronoUnit.HOURS).toString();
        String to = Instant.now().plus(1, ChronoUnit.HOURS).toString();
        String inside = ok(call(HttpMethod.GET, "/payments?from=" + from + "&to=" + to, cashierToken, null));
        assertThat(this.<List<String>>json(inside, "$[*].id")).contains(cash, transfer);
        String future = ok(call(HttpMethod.GET, "/payments?from=" + to, cashierToken, null));
        assertThat(this.<List<String>>json(future, "$[*].id")).doesNotContain(cash, transfer);
        String past = ok(call(HttpMethod.GET, "/payments?to=" + from, cashierToken, null));
        assertThat(this.<List<String>>json(past, "$[*].id")).doesNotContain(cash, transfer);

        call(HttpMethod.GET, "/payments?status=BOGUS", cashierToken, null).andExpect(status().isBadRequest());
        call(HttpMethod.GET, "/payments?method=BOGUS", cashierToken, null).andExpect(status().isBadRequest());
        call(HttpMethod.GET, "/payments?from=yesterday", cashierToken, null).andExpect(status().isBadRequest());
    }

    @Test
    void receiptListsServedLinesTotalsAndSuccessfulPayments() throws Exception {
        ensureSettings();
        Fixture f = billFixture();
        item(f.order(), f.menu(), com.vertex.restosphere.entity.OrderItem.Status.CANCELLED);
        long before = revision();

        String unpaid = ok(call(HttpMethod.GET, "/bills/" + f.bill().getId() + "/receipt", cashierToken, null));
        assertThat(revision()).isEqualTo(before);
        assertThat(this.<String>json(unpaid, "$.billCode")).isEqualTo(f.bill().getCode());
        assertThat(this.<String>json(unpaid, "$.tableCode")).isEqualTo(f.table().getCode());
        assertThat(this.<Integer>json(unpaid, "$.guests")).isEqualTo(2);
        assertThat(this.<String>json(unpaid, "$.status")).isEqualTo("PAYMENT_REQUESTED");
        assertThat(this.<String>json(unpaid, "$.waiter")).isEqualTo(waiter.getFullName());
        assertThat(this.<String>json(unpaid, "$.printedBy")).isEqualTo(cashier.getFullName());
        assertThat(this.<List<Object>>json(unpaid, "$.items")).hasSize(1);
        assertThat(this.<Number>json(unpaid, "$.items[0].lineTotal").longValue()).isEqualTo(100000L);
        assertThat(this.<Number>json(unpaid, "$.totals.total").longValue()).isEqualTo(100000L);
        assertThat(this.<Number>json(unpaid, "$.remaining").longValue()).isEqualTo(100000L);
        assertThat(this.<List<Object>>json(unpaid, "$.payments")).isEmpty();

        openShift(cashierToken, 0);
        pay(f, 100000, "c7-receipt");
        String paid = ok(call(HttpMethod.GET, "/bills/" + f.bill().getId() + "/receipt", cashierToken, null));
        assertThat(this.<String>json(paid, "$.status")).isEqualTo("PAID");
        assertThat(this.<Number>json(paid, "$.paid").longValue()).isEqualTo(100000L);
        assertThat(this.<Number>json(paid, "$.remaining").longValue()).isZero();
        assertThat(this.<List<Object>>json(paid, "$.payments")).hasSize(1);
        assertThat(this.<String>json(paid, "$.payments[0].method")).isEqualTo("CASH");
        assertThat(this.<Number>json(paid, "$.payments[0].change").longValue()).isEqualTo(5000L);
    }

    @Test
    void receiptOfUnknownBillIs404() throws Exception {
        call(HttpMethod.GET, "/bills/" + UUID.randomUUID() + "/receipt", cashierToken, null)
                .andExpect(status().isNotFound());
        call(HttpMethod.GET, "/bills/not-a-uuid/receipt", cashierToken, null).andExpect(status().isBadRequest());
    }
}
