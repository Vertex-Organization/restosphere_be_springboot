package com.vertex.restosphere;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vertex.restosphere.entity.Bill;
import com.vertex.restosphere.entity.Payment;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class CashierPayosLinkCancelApiIntegrationTests extends CashierApiTestBase {
    @Override
    String prefix() {
        return "p2";
    }

    private String linkPath(Fixture f) {
        return "/bills/" + f.bill().getId() + "/payos-link";
    }

    private String cancelPath(Fixture f) {
        return linkPath(f) + "/cancel";
    }

    private long version(Fixture f) {
        return tx.execute(s -> em.find(Bill.class, f.bill().getId()).getVersion());
    }

    private void createLink(Fixture f) throws Exception {
        created(
                call(
                        HttpMethod.POST,
                        linkPath(f),
                        cashierToken,
                        "{\"billVersion\":%d}".formatted(version(f))));
    }

    private Bill.Status billStatus(Fixture f) {
        return tx.execute(s -> em.find(Bill.class, f.bill().getId()).getStatus());
    }

    private List<Payment> payments(Fixture f) {
        return tx.execute(
                s ->
                        em.createQuery(
                                        "from Payment p where p.bill.id = :id order by p.createdAt",
                                        Payment.class)
                                .setParameter("id", f.bill().getId())
                                .getResultList());
    }

    @Test
    void authIsEnforced() throws Exception {
        Fixture f = billFixture();
        call(HttpMethod.POST, cancelPath(f), null, null).andExpect(status().isUnauthorized());
        call(HttpMethod.POST, cancelPath(f), waiterToken, null).andExpect(status().isForbidden());
    }

    @Test
    void cancellingThePendingQrFreesTheBillForCashAndKeepsItsData() throws Exception {
        Fixture f = billFixture();
        openShift(cashierToken, 0);
        createLink(f);
        long revisionBefore = revision();

        String body = ok(call(HttpMethod.POST, cancelPath(f), cashierToken, null));

        assertThat(this.<String>json(body, "$.method")).isEqualTo("PAYOS");
        assertThat(this.<String>json(body, "$.status")).isEqualTo("CANCELLED");
        var all = payments(f);
        assertThat(all).hasSize(1);
        assertThat(all.getFirst().getStatus()).isEqualTo(Payment.Status.CANCELLED);
        assertThat(revision()).isGreaterThan(revisionBefore);
        assertThat(auditCount("cancel-payos-link")).isEqualTo(1);
        Bill bill = tx.execute(s -> em.find(Bill.class, f.bill().getId()));
        assertThat(bill.getStatus()).isEqualTo(Bill.Status.OPEN);
        assertThat(bill.getTotal()).isEqualByComparingTo(new BigDecimal("100000"));

        created(
                call(
                        HttpMethod.POST,
                        "/bills/" + f.bill().getId() + "/payments/cash",
                        cashierToken,
                        "{\"billVersion\":%d,\"amount\":100000,\"tendered\":100000,\"idempotencyKey\":\"p2-cash\"}"
                                .formatted(version(f))));
        assertThat(billStatus(f)).isEqualTo(Bill.Status.PAID);
    }

    @Test
    void withoutAPendingQrItIsAConflict() throws Exception {
        Fixture f = billFixture();
        openShift(cashierToken, 0);
        call(HttpMethod.POST, cancelPath(f), cashierToken, null)
                .andExpect(status().isConflict());
        createLink(f);
        ok(call(HttpMethod.POST, cancelPath(f), cashierToken, null));
        call(HttpMethod.POST, cancelPath(f), cashierToken, null)
                .andExpect(status().isConflict());
        assertThat(auditCount("cancel-payos-link")).isEqualTo(1);
    }
}
