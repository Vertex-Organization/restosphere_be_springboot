package com.vertex.restosphere;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.entity.Bill;
import com.vertex.restosphere.entity.OrderItem;
import com.vertex.restosphere.entity.User;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class CashierCashPaymentApiIntegrationTests extends CashierApiTestBase {
    @Override
    String prefix() {
        return "c3";
    }

    private String cashJson(long version, long amount, long tendered, String key) {
        return "{\"billVersion\":%d,\"amount\":%d,\"tendered\":%d,\"idempotencyKey\":\"%s\"}".formatted(version,
                amount, tendered, key);
    }

    private String payPath(Fixture f) {
        return "/bills/" + f.bill().getId() + "/payments/cash";
    }

    private long version(Fixture f) {
        return tx.execute(s -> em.find(Bill.class, f.bill().getId()).getVersion());
    }

    private Bill.Status billStatus(Fixture f) {
        return tx.execute(s -> em.find(Bill.class, f.bill().getId()).getStatus());
    }

    private long waiterNotifications() {
        return count("select count(n) from Notification n where n.kind = 'PAYMENT' and n.audienceRole = :p0",
                Role.WAITER);
    }

    @Test
    void authIsEnforced() throws Exception {
        Fixture f = billFixture();
        call(HttpMethod.POST, payPath(f), null, cashJson(0, 1, 1, "k")).andExpect(status().isUnauthorized());
        call(HttpMethod.POST, payPath(f), waiterToken, cashJson(0, 1, 1, "k")).andExpect(status().isForbidden());
    }

    @Test
    void fullCashPaymentSettlesBillNotifiesWaiterBumpsRevisionAndAudits() throws Exception {
        Fixture f = billFixture();
        String shiftId = openShift(cashierToken, 0);
        long before = revision();

        String body = created(call(HttpMethod.POST, payPath(f), cashierToken,
                cashJson(version(f), 100000, 150000, "c3-full")));
        assertThat(this.<String>json(body, "$.status")).isEqualTo("SUCCEEDED");
        assertThat(this.<String>json(body, "$.method")).isEqualTo("CASH");
        assertThat(this.<Number>json(body, "$.change").longValue()).isEqualTo(50000L);
        assertThat(this.<String>json(body, "$.shiftId")).isEqualTo(shiftId);
        assertThat(this.<String>json(body, "$.billId")).isEqualTo(f.bill().getId().toString());
        assertThat(this.<String>json(body, "$.createdBy")).isEqualTo(cashier.getId().toString());

        assertThat(billStatus(f)).isEqualTo(Bill.Status.PAID);
        assertThat(revision()).isEqualTo(before + 1);
        assertThat(auditCount("pay-cash")).isEqualTo(1);
        assertThat(waiterNotifications()).isEqualTo(1);

        String bill = ok(call(HttpMethod.GET, "/bills/" + f.bill().getId(), cashierToken, null));
        assertThat(this.<String>json(bill, "$.status")).isEqualTo("PAID");
        assertThat(this.<Number>json(bill, "$.paid").longValue()).isEqualTo(100000L);
        assertThat(this.<Number>json(bill, "$.remaining").longValue()).isZero();

        String close = ok(call(HttpMethod.POST, "/shifts/" + shiftId + "/close", cashierToken,
                "{\"countedCash\":100000}"));
        assertThat(this.<Number>json(close, "$.expectedCash").longValue()).isEqualTo(100000L);
    }

    @Test
    void partialPaymentKeepsBillOpenAndDoesNotNotify() throws Exception {
        Fixture f = billFixture();
        openShift(cashierToken, 0);
        created(call(HttpMethod.POST, payPath(f), cashierToken, cashJson(version(f), 40000, 40000, "c3-part")));
        assertThat(billStatus(f)).isEqualTo(Bill.Status.OPEN);
        assertThat(waiterNotifications()).isZero();

        String bill = ok(call(HttpMethod.GET, "/bills/" + f.bill().getId(), cashierToken, null));
        assertThat(this.<Number>json(bill, "$.remaining").longValue()).isEqualTo(60000L);

        call(HttpMethod.POST, payPath(f), cashierToken, cashJson(version(f), 60001, 70000, "c3-over"))
                .andExpect(status().isBadRequest());
        created(call(HttpMethod.POST, payPath(f), cashierToken, cashJson(version(f), 60000, 60000, "c3-rest")));
        assertThat(billStatus(f)).isEqualTo(Bill.Status.PAID);
        assertThat(waiterNotifications()).isEqualTo(1);
    }

    @Test
    void sameKeyReturnsTheEarlierPaymentWithoutChargingTwice() throws Exception {
        Fixture f = billFixture();
        openShift(cashierToken, 0);
        String first = created(call(HttpMethod.POST, payPath(f), cashierToken,
                cashJson(version(f), 30000, 30000, "c3-idem")));
        long revisionAfterFirst = revision();

        String replay = ok(call(HttpMethod.POST, payPath(f), cashierToken,
                cashJson(0, 30000, 30000, "c3-idem")));
        assertThat(this.<String>json(replay, "$.id")).isEqualTo(this.<String>json(first, "$.id"));
        assertThat(revision()).isEqualTo(revisionAfterFirst);
        assertThat(count("select count(p) from Payment p where p.bill.id = :p0", f.bill().getId())).isEqualTo(1);

        call(HttpMethod.POST, payPath(f), cashierToken, cashJson(version(f), 31000, 31000, "c3-idem"))
                .andExpect(status().isConflict());
        call(HttpMethod.POST, payPath(f), cashierToken, cashJson(version(f), 30000, 35000, "c3-idem"))
                .andExpect(status().isConflict());
        Fixture other = billFixture();
        call(HttpMethod.POST, payPath(other), cashierToken, cashJson(version(other), 30000, 30000, "c3-idem"))
                .andExpect(status().isConflict());
    }

    @Test
    void idempotencyKeyOfAnotherCashierIsConflict() throws Exception {
        Fixture f = billFixture();
        openShift(cashierToken, 0);
        created(call(HttpMethod.POST, payPath(f), cashierToken, cashJson(version(f), 30000, 30000, "c3-owner")));
        User other = saveUser("c3-cashier2@restosphere.test", Role.CASHIER);
        String otherToken = bearer(other.getEmail());
        call(HttpMethod.POST, payPath(f), otherToken, cashJson(version(f), 30000, 30000, "c3-owner"))
                .andExpect(status().isConflict());
    }

    @Test
    void staleBillVersionIsConflict() throws Exception {
        Fixture f = billFixture();
        openShift(cashierToken, 0);
        call(HttpMethod.POST, payPath(f), cashierToken, cashJson(version(f) + 5, 1000, 1000, "c3-ver"))
                .andExpect(status().isConflict());
        assertThat(count("select count(p) from Payment p where p.bill.id = :p0", f.bill().getId())).isZero();
    }

    @Test
    void paymentNeedsAnOpenShift() throws Exception {
        Fixture f = billFixture();
        call(HttpMethod.POST, payPath(f), cashierToken, cashJson(version(f), 1000, 1000, "c3-noshift"))
                .andExpect(status().isConflict());
        String shiftId = openShift(cashierToken, 0);
        call(HttpMethod.POST, "/shifts/" + shiftId + "/close", cashierToken, "{\"countedCash\":0}")
                .andExpect(status().isOk());
        call(HttpMethod.POST, payPath(f), cashierToken, cashJson(version(f), 1000, 1000, "c3-closed"))
                .andExpect(status().isConflict());
    }

    @Test
    void billMustAwaitPaymentAndHaveNoUnservedDishes() throws Exception {
        openShift(cashierToken, 0);
        Fixture waiting = billFixture();
        var session = waiting.session();
        session.setStatus(com.vertex.restosphere.entity.TableSession.Status.OPEN);
        persist(session);
        call(HttpMethod.POST, payPath(waiting), cashierToken, cashJson(version(waiting), 1000, 1000, "c3-open"))
                .andExpect(status().isConflict());

        Fixture unserved = billFixture();
        OrderItem pending = item(unserved.order(), unserved.menu(), OrderItem.Status.READY);
        call(HttpMethod.POST, payPath(unserved), cashierToken,
                cashJson(version(unserved), 1000, 1000, "c3-unserved")).andExpect(status().isConflict());
        assertThat(pending.getStatus()).isEqualTo(OrderItem.Status.READY);
    }

    @Test
    void invalidAmountsAndBodyAreRejected() throws Exception {
        Fixture f = billFixture();
        openShift(cashierToken, 0);
        long v = version(f);
        call(HttpMethod.POST, payPath(f), cashierToken, cashJson(v, 0, 0, "c3-zero")).andExpect(status().isBadRequest());
        call(HttpMethod.POST, payPath(f), cashierToken, cashJson(v, -5, 10, "c3-neg")).andExpect(status().isBadRequest());
        call(HttpMethod.POST, payPath(f), cashierToken, cashJson(v, 100001, 200000, "c3-big"))
                .andExpect(status().isBadRequest());
        call(HttpMethod.POST, payPath(f), cashierToken, cashJson(v, 50000, 49999, "c3-short"))
                .andExpect(status().isBadRequest());
        call(HttpMethod.POST, payPath(f), cashierToken,
                "{\"billVersion\":%d,\"amount\":1000,\"tendered\":1000}".formatted(v))
                .andExpect(status().isBadRequest());
        call(HttpMethod.POST, payPath(f), cashierToken, cashJson(v, 1000, 1000, "  "))
                .andExpect(status().isBadRequest());
        call(HttpMethod.POST, "/bills/" + UUID.randomUUID() + "/payments/cash", cashierToken,
                cashJson(0, 1000, 1000, "c3-404")).andExpect(status().isNotFound());
        assertThat(count("select count(p) from Payment p where p.bill.id = :p0", f.bill().getId())).isZero();
    }
}
