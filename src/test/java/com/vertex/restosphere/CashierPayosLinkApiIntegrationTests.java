package com.vertex.restosphere;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vertex.restosphere.entity.Bill;
import com.vertex.restosphere.entity.Payment;
import com.vertex.restosphere.service.PayosGateway;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

class CashierPayosLinkApiIntegrationTests extends CashierApiTestBase {
    @Autowired PayosGateway gateway;

    @Override
    String prefix() {
        return "p1";
    }

    private String path(Fixture f) {
        return "/bills/" + f.bill().getId() + "/payos-link";
    }

    private long version(Fixture f) {
        return tx.execute(s -> em.find(Bill.class, f.bill().getId()).getVersion());
    }

    private String link(Fixture f) throws Exception {
        return created(
                call(
                        HttpMethod.POST,
                        path(f),
                        cashierToken,
                        "{\"billVersion\":%d}".formatted(version(f))));
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
        call(HttpMethod.POST, path(f), null, "{\"billVersion\":0}")
                .andExpect(status().isUnauthorized());
        call(HttpMethod.POST, path(f), waiterToken, "{\"billVersion\":0}")
                .andExpect(status().isForbidden());
    }

    @Test
    void linkIsPendingForTheServerComputedAmountAndTiedToTheShift() throws Exception {
        Fixture f = billFixture();
        String shiftId = openShift(cashierToken, 0);

        String body = link(f);

        assertThat(this.<String>json(body, "$.method")).isEqualTo("PAYOS");
        assertThat(this.<String>json(body, "$.status")).isEqualTo("PENDING");
        assertThat(this.<Number>json(body, "$.amount").longValue()).isEqualTo(100000L);
        assertThat(this.<String>json(body, "$.shiftId")).isEqualTo(shiftId);
        assertThat(this.<String>json(body, "$.checkoutUrl")).startsWith("https://");
        var saved = payments(f).getFirst();
        assertThat(saved.getPayosOrderCode()).isNotNull();
        assertThat(saved.getQrCode()).isNotBlank();
        assertThat(saved.getCreatedBy().getId()).isEqualTo(cashier.getId());
        assertThat(auditCount("create-payos-link")).isEqualTo(1);
        assertThat(billStatus(f)).isEqualTo(Bill.Status.OPEN);
    }

    @Test
    void printingTwiceForTheSameAmountKeepsOneLink() throws Exception {
        Fixture f = billFixture();
        openShift(cashierToken, 0);
        String first = link(f);
        String second =
                ok(
                        call(
                                HttpMethod.POST,
                                path(f),
                                cashierToken,
                                "{\"billVersion\":%d}".formatted(version(f))));
        assertThat(this.<String>json(second, "$.id")).isEqualTo(this.<String>json(first, "$.id"));
        assertThat(payments(f)).hasSize(1);
    }

    @Test
    void aBillWithALiveLinkRefusesCashSoTheGuestCannotPayTwice() throws Exception {
        Fixture f = billFixture();
        openShift(cashierToken, 0);
        link(f);
        call(
                        HttpMethod.POST,
                        "/bills/" + f.bill().getId() + "/payments/cash",
                        cashierToken,
                        "{\"billVersion\":%d,\"amount\":100000,\"tendered\":100000,\"idempotencyKey\":\"p1-cash\"}"
                                .formatted(version(f)))
                .andExpect(status().isConflict());
    }

    @Test
    void whenTheBillChangesTheOldLinkIsCancelledAndANewOneIsMade() throws Exception {
        Fixture f = billFixture();
        openShift(cashierToken, 0);
        String first = link(f);
        long oldCode = payments(f).getFirst().getPayosOrderCode();

        item(f.order(), f.menu(), com.vertex.restosphere.entity.OrderItem.Status.SERVED);
        String second = link(f);

        var all = payments(f);
        assertThat(all).hasSize(2);
        assertThat(all.get(0).getStatus()).isEqualTo(Payment.Status.CANCELLED);
        assertThat(all.get(1).getStatus()).isEqualTo(Payment.Status.PENDING);
        assertThat(all.get(1).getAmount()).isEqualByComparingTo(new BigDecimal("200000"));
        assertThat(gateway.status(oldCode).status()).isEqualTo(PayosGateway.LinkStatus.CANCELLED);
        assertThat(this.<String>json(second, "$.id"))
                .isNotEqualTo(this.<String>json(first, "$.id"));
    }

    @Test
    void needsAnOpenShiftAndABillAwaitingPayment() throws Exception {
        Fixture f = billFixture();
        call(HttpMethod.POST, path(f), cashierToken, "{\"billVersion\":%d}".formatted(version(f)))
                .andExpect(status().isConflict());
        openShift(cashierToken, 0);
        call(
                        HttpMethod.POST,
                        path(f),
                        cashierToken,
                        "{\"billVersion\":%d}".formatted(version(f) + 5))
                .andExpect(status().isConflict());
        assertThat(payments(f)).isEmpty();
    }

    @Test
    void closingTheShiftIsBlockedWhileALinkIsPending() throws Exception {
        Fixture f = billFixture();
        String shiftId = openShift(cashierToken, 0);
        link(f);
        call(HttpMethod.POST, "/shifts/" + shiftId + "/close", cashierToken, "{\"countedCash\":0}")
                .andExpect(status().isConflict());
    }

    private Bill.Status billStatus(Fixture f) {
        return tx.execute(s -> em.find(Bill.class, f.bill().getId()).getStatus());
    }
}
