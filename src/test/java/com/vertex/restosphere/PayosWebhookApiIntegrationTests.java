package com.vertex.restosphere;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vertex.restosphere.entity.Bill;
import com.vertex.restosphere.entity.Payment;
import com.vertex.restosphere.entity.TableSession;
import com.vertex.restosphere.service.PayosPayments;
import com.vertex.restosphere.service.PayosPayments.Outcome;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

class PayosWebhookApiIntegrationTests extends CashierApiTestBase {
    @Autowired PayosPayments payosPayments;

    @Override
    String prefix() {
        return "p2";
    }

    private Fixture fixtureWithLink() throws Exception {
        Fixture f = billFixture();
        openShift(cashierToken, 0);
        long version = tx.execute(s -> em.find(Bill.class, f.bill().getId()).getVersion());
        created(
                call(
                        HttpMethod.POST,
                        "/bills/" + f.bill().getId() + "/payos-link",
                        cashierToken,
                        "{\"billVersion\":%d}".formatted(version)));
        return f;
    }

    private Payment payment(Fixture f) {
        return tx.execute(
                s ->
                        em.createQuery("from Payment p where p.bill.id = :id", Payment.class)
                                .setParameter("id", f.bill().getId())
                                .getResultList()
                                .getFirst());
    }

    private Bill bill(Fixture f) {
        return tx.execute(
                s -> {
                    Bill b = em.find(Bill.class, f.bill().getId());
                    b.getSessions().forEach(x -> x.getTable().getCode());
                    return b;
                });
    }

    @Test
    void anUnsignedWebhookIsRejectedWith400AndChangesNothing() throws Exception {
        Fixture f = fixtureWithLink();
        mockMvc.perform(
                        post("/api/payments/payos-webhook")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"code\":\"00\",\"desc\":\"success\",\"success\":true,"
                                                + "\"data\":{\"orderCode\":1,\"amount\":1},\"signature\":\"x\"}"))
                .andExpect(status().is4xxClientError());
        assertThat(payment(f).getStatus()).isEqualTo(Payment.Status.PENDING);
    }

    @Test
    void theWebhookEndpointNeedsNoToken() throws Exception {
        int code =
                mockMvc.perform(
                                post("/api/payments/payos-webhook")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{}"))
                        .andReturn()
                        .getResponse()
                        .getStatus();
        assertThat(code).isNotIn(401, 403);
    }

    @Test
    void theDevSimulatorIsNotExposedOutsideTheDevProfile() throws Exception {
        mockMvc.perform(post("/api/dev/payos/fake-paid/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void paymentCompletesTheBillClosesTheSessionAndRepeatsAreIgnored() throws Exception {
        Fixture f = fixtureWithLink();
        long code = payment(f).getPayosOrderCode();

        assertThat(payosPayments.simulatePaid(code)).isEqualTo(Outcome.COMPLETED);

        Payment p = payment(f);
        assertThat(p.getStatus()).isEqualTo(Payment.Status.COMPLETED);
        assertThat(p.getPaidAt()).isNotNull();
        assertThat(bill(f).getStatus()).isEqualTo(Bill.Status.PAID);
        var session = tx.execute(s -> em.find(TableSession.class, f.session().getId()));
        assertThat(session.getStatus()).isEqualTo(TableSession.Status.CLOSED);
        assertThat(
                        count(
                                "select count(n) from Notification n where n.kind = 'PAYMENT'"
                                        + " and n.path = :p0",
                                f.session().getId().toString()))
                .isEqualTo(1);

        assertThat(payosPayments.simulatePaid(code)).isEqualTo(Outcome.ALREADY_COMPLETED);
        assertThat(
                        count(
                                "select count(n) from Notification n where n.kind = 'PAYMENT'"
                                        + " and n.path = :p0",
                                f.session().getId().toString()))
                .isEqualTo(1);
    }

    @Test
    void aWrongAmountIsNotAcceptedAndTheManagerIsTold() throws Exception {
        Fixture f = fixtureWithLink();
        long code = payment(f).getPayosOrderCode();

        assertThat(payosPayments.markPaid(code, 1000, "REF-1")).isEqualTo(Outcome.AMOUNT_MISMATCH);

        assertThat(payment(f).getStatus()).isEqualTo(Payment.Status.PENDING);
        assertThat(bill(f).getStatus()).isEqualTo(Bill.Status.OPEN);
        assertThat(
                        count(
                                "select count(n) from Notification n where n.kind ="
                                        + " 'PAYOS_AMOUNT_MISMATCH'"))
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    void anUnknownOrderCodeIsAcceptedQuietly() {
        assertThat(payosPayments.markPaid(999_999_999_999L, 1, "REF")).isEqualTo(Outcome.UNKNOWN);
    }

    @Test
    void moneyForACancelledLinkIsNeverRevivedAndOnlyFlaggedOnce() throws Exception {
        Fixture f = fixtureWithLink();
        Payment before = payment(f);
        tx.executeWithoutResult(
                t ->
                        em.createQuery("update Payment p set p.status = :s where p.id = :id")
                                .setParameter("s", Payment.Status.CANCELLED)
                                .setParameter("id", before.getId())
                                .executeUpdate());

        long code = before.getPayosOrderCode();
        assertThat(payosPayments.markPaid(code, 100000, "LATE-1")).isEqualTo(Outcome.LATE_PAYMENT);
        assertThat(payosPayments.markPaid(code, 100000, "LATE-1")).isEqualTo(Outcome.LATE_PAYMENT);

        assertThat(payment(f).getStatus()).isEqualTo(Payment.Status.CANCELLED);
        assertThat(bill(f).getStatus()).isEqualTo(Bill.Status.OPEN);
        assertThat(
                        count(
                                "select count(n) from Notification n where n.kind = 'LATE_PAYMENT'"
                                        + " and n.path = :p0",
                                before.getId().toString()))
                .isEqualTo(1);
    }

    @Test
    void refreshAsksPayosAndUpdatesLikeTheWebhook() throws Exception {
        Fixture f = fixtureWithLink();
        Payment p = payment(f);
        assertThat(refresh(p)).isEqualTo(Outcome.STILL_PENDING);

        ((com.vertex.restosphere.service.impl.FakePayosGateway) gateway)
                .markPaid(p.getPayosOrderCode());

        assertThat(refresh(p)).isEqualTo(Outcome.COMPLETED);
        assertThat(bill(f).getStatus()).isEqualTo(Bill.Status.PAID);
        assertThat(refresh(p)).isEqualTo(Outcome.ALREADY_COMPLETED);
    }

    @Autowired com.vertex.restosphere.service.PayosGateway gateway;

    private Outcome refresh(Payment p) {
        return tx.execute(s -> payosPayments.refresh(em.find(Payment.class, p.getId())));
    }
}
