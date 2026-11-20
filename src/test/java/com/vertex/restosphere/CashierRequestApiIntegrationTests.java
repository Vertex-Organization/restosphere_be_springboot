package com.vertex.restosphere;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.entity.Bill;
import com.vertex.restosphere.entity.OrderItem;
import com.vertex.restosphere.entity.TableSession;
import com.vertex.restosphere.entity.User;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class CashierRequestApiIntegrationTests extends CashierApiTestBase {
    @Override
    String prefix() {
        return "c6";
    }

    private long version(Fixture f) {
        return tx.execute(s -> em.find(Bill.class, f.bill().getId()).getVersion());
    }

    private String adjustment(Fixture f, String itemId, String reason) {
        return "{\"itemId\":\"%s\",\"reason\":\"%s\"}".formatted(itemId, reason);
    }

    private String adjustmentPath(Fixture f) {
        return "/bills/" + f.bill().getId() + "/adjustment-requests";
    }

    private String pay(Fixture f, long amount, String key) throws Exception {
        String body = created(call(HttpMethod.POST, "/bills/" + f.bill().getId() + "/payments/cash", cashierToken,
                "{\"billVersion\":%d,\"amount\":%d,\"tendered\":%d,\"idempotencyKey\":\"%s\"}".formatted(version(f),
                        amount, amount, key)));
        return this.<String>json(body, "$.id");
    }

    @Test
    void authIsEnforced() throws Exception {
        Fixture f = billFixture();
        call(HttpMethod.GET, "/requests", null, null).andExpect(status().isUnauthorized());
        call(HttpMethod.GET, "/requests", waiterToken, null).andExpect(status().isForbidden());
        call(HttpMethod.POST, adjustmentPath(f), waiterToken, adjustment(f, f.item().getId().toString(), "r"))
                .andExpect(status().isForbidden());
        call(HttpMethod.POST, "/payments/" + UUID.randomUUID() + "/void-requests", waiterToken, "{\"reason\":\"r\"}")
                .andExpect(status().isForbidden());
        call(HttpMethod.POST, "/requests/" + UUID.randomUUID() + "/withdraw", waiterToken, null)
                .andExpect(status().isForbidden());
        call(HttpMethod.POST, "/bills/" + f.bill().getId() + "/return-payment-request", waiterToken,
                "{\"billVersion\":0,\"reason\":\"r\"}").andExpect(status().isForbidden());
    }

    @Test
    void adjustmentRequestIsCreatedForABillLineAndRejectsBadLines() throws Exception {
        Fixture f = billFixture();
        long before = revision();
        String res = created(call(HttpMethod.POST, adjustmentPath(f), cashierToken,
                adjustment(f, f.item().getId().toString(), "wrong dish")));
        assertThat(this.<String>json(res, "$.type")).isEqualTo("BILL_ADJUSTMENT");
        assertThat(this.<String>json(res, "$.status")).isEqualTo("PENDING");
        assertThat(this.<String>json(res, "$.targetType")).isEqualTo("ORDER_ITEM");
        assertThat(this.<String>json(res, "$.itemId")).isEqualTo(f.item().getId().toString());
        assertThat(this.<String>json(res, "$.billId")).isEqualTo(f.bill().getId().toString());
        assertThat(this.<Number>json(res, "$.amount").longValue()).isEqualTo(100000L);
        assertThat(revision()).isEqualTo(before + 1);
        assertThat(auditCount("request-adjustment")).isEqualTo(1);

        call(HttpMethod.POST, adjustmentPath(f), cashierToken, adjustment(f, f.item().getId().toString(), "again"))
                .andExpect(status().isConflict());
        call(HttpMethod.POST, adjustmentPath(f), cashierToken, adjustment(f, " ", "r"))
                .andExpect(status().isBadRequest());
        call(HttpMethod.POST, adjustmentPath(f), cashierToken, adjustment(f, "not-a-uuid", "r"))
                .andExpect(status().isBadRequest());
        call(HttpMethod.POST, adjustmentPath(f), cashierToken, adjustment(f, UUID.randomUUID().toString(), "r"))
                .andExpect(status().isNotFound());

        Fixture other = billFixture();
        call(HttpMethod.POST, adjustmentPath(f), cashierToken, adjustment(f, other.item().getId().toString(), "r"))
                .andExpect(status().isBadRequest());
        OrderItem removed = item(f.order(), f.menu(), OrderItem.Status.CANCELLED);
        call(HttpMethod.POST, adjustmentPath(f), cashierToken, adjustment(f, removed.getId().toString(), "r"))
                .andExpect(status().isBadRequest());
        OrderItem fresh = item(f.order(), f.menu(), OrderItem.Status.SERVED);
        call(HttpMethod.POST, adjustmentPath(f), cashierToken, adjustment(f, fresh.getId().toString(), " "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adjustmentNeedsAnEditableBill() throws Exception {
        Fixture f = billFixture();
        openShift(cashierToken, 0);
        pay(f, 1000, "c6-adj");
        call(HttpMethod.POST, adjustmentPath(f), cashierToken, adjustment(f, f.item().getId().toString(), "r"))
                .andExpect(status().isConflict());
    }

    @Test
    void voidRequestTargetsASuccessfulPayment() throws Exception {
        Fixture f = billFixture();
        openShift(cashierToken, 0);
        String paymentId = pay(f, 100000, "c6-void");
        long before = revision();
        String res = created(call(HttpMethod.POST, "/payments/" + paymentId + "/void-requests", cashierToken,
                "{\"reason\":\"guest paid twice\"}"));
        assertThat(this.<String>json(res, "$.type")).isEqualTo("PAYMENT_VOID");
        assertThat(this.<String>json(res, "$.paymentId")).isEqualTo(paymentId);
        assertThat(this.<String>json(res, "$.billId")).isEqualTo(f.bill().getId().toString());
        assertThat(this.<Number>json(res, "$.amount").longValue()).isEqualTo(100000L);
        assertThat(revision()).isEqualTo(before + 1);
        assertThat(auditCount("request-void")).isEqualTo(1);

        call(HttpMethod.POST, "/payments/" + paymentId + "/void-requests", cashierToken, "{\"reason\":\"again\"}")
                .andExpect(status().isConflict());
        call(HttpMethod.POST, "/payments/" + UUID.randomUUID() + "/void-requests", cashierToken,
                "{\"reason\":\"r\"}").andExpect(status().isNotFound());
    }

    @Test
    void voidRequestRejectsNonSuccessfulPaymentAndBlankReason() throws Exception {
        Fixture f = billFixture();
        openShift(cashierToken, 0);
        String paymentId = pay(f, 1000, "c6-void2");
        call(HttpMethod.POST, "/payments/" + paymentId + "/void-requests", cashierToken, "{\"reason\":\" \"}")
                .andExpect(status().isBadRequest());
        call(HttpMethod.POST, "/payments/" + paymentId + "/void-requests", cashierToken, "{}")
                .andExpect(status().isBadRequest());
        tx.executeWithoutResult(t -> em.createQuery("update Payment p set p.status = :s where p.id = :id")
                .setParameter("s", com.vertex.restosphere.entity.Payment.Status.VOIDED)
                .setParameter("id", UUID.fromString(paymentId)).executeUpdate());
        call(HttpMethod.POST, "/payments/" + paymentId + "/void-requests", cashierToken, "{\"reason\":\"r\"}")
                .andExpect(status().isConflict());
    }

    @Test
    void withdrawOnlyOwnPendingRequests() throws Exception {
        Fixture f = billFixture();
        String created = created(call(HttpMethod.POST, adjustmentPath(f), cashierToken,
                adjustment(f, f.item().getId().toString(), "oops")));
        String id = this.<String>json(created, "$.id");
        User other = saveUser("c6-cashier2@restosphere.test", Role.CASHIER);
        String otherToken = bearer(other.getEmail());
        call(HttpMethod.POST, "/requests/" + id + "/withdraw", otherToken, null).andExpect(status().isConflict());

        long before = revision();
        String res = ok(call(HttpMethod.POST, "/requests/" + id + "/withdraw", cashierToken, null));
        assertThat(this.<String>json(res, "$.status")).isEqualTo("WITHDRAWN");
        assertThat(this.<String>json(res, "$.billId")).isEqualTo(f.bill().getId().toString());
        assertThat(revision()).isEqualTo(before + 1);
        assertThat(auditCount("withdraw-request")).isEqualTo(1);
        call(HttpMethod.POST, "/requests/" + id + "/withdraw", cashierToken, null).andExpect(status().isConflict());
        call(HttpMethod.POST, "/requests/" + UUID.randomUUID() + "/withdraw", cashierToken, null)
                .andExpect(status().isNotFound());

        created(call(HttpMethod.POST, adjustmentPath(f), cashierToken,
                adjustment(f, f.item().getId().toString(), "retry")));
    }

    @Test
    void listResolvesBillPaymentAndItemOfEachRequestKind() throws Exception {
        Fixture f = billFixture();
        openShift(cashierToken, 0);
        String paymentId = pay(f, 1000, "c6-list");
        Fixture g = billFixture();
        created(call(HttpMethod.POST, "/bills/" + g.bill().getId() + "/discount-requests", cashierToken,
                "{\"billVersion\":%d,\"discount\":{\"mode\":\"PERCENT\",\"value\":20,\"reason\":\"vip\"}}"
                        .formatted(version(g))));
        created(call(HttpMethod.POST, adjustmentPath(g), cashierToken,
                adjustment(g, g.item().getId().toString(), "wrong")));
        created(call(HttpMethod.POST, "/payments/" + paymentId + "/void-requests", cashierToken,
                "{\"reason\":\"twice\"}"));

        String body = ok(call(HttpMethod.GET, "/requests", cashierToken, null));
        List<String> types = json(body, "$[?(@.billId=='%s')].type".formatted(g.bill().getId()));
        assertThat(types).containsExactlyInAnyOrder("DISCOUNT", "BILL_ADJUSTMENT");
        List<String> voidPayments = json(body, "$[?(@.type=='PAYMENT_VOID' && @.billId=='%s')].paymentId"
                .formatted(f.bill().getId()));
        assertThat(voidPayments).containsExactly(paymentId);
        List<String> items = json(body, "$[?(@.type=='BILL_ADJUSTMENT' && @.billId=='%s')].itemId"
                .formatted(g.bill().getId()));
        assertThat(items).containsExactly(g.item().getId().toString());
    }

    @Test
    void returnPaymentRequestGivesTheBillBackToTheWaiter() throws Exception {
        Fixture f = billFixture();
        String path = "/bills/" + f.bill().getId() + "/return-payment-request";
        long before = revision();
        call(HttpMethod.POST, path, cashierToken, "{\"billVersion\":%d,\"reason\":\" \"}".formatted(version(f)))
                .andExpect(status().isBadRequest());
        call(HttpMethod.POST, path, cashierToken, "{\"billVersion\":%d,\"reason\":\"r\"}".formatted(version(f) + 4))
                .andExpect(status().isConflict());
        assertThat(revision()).isEqualTo(before);

        String res = ok(call(HttpMethod.POST, path, cashierToken,
                "{\"billVersion\":%d,\"reason\":\"guest wants more\"}".formatted(version(f))));
        assertThat(this.<String>json(res, "$.status")).isEqualTo("UNPAID");
        assertThat(revision()).isEqualTo(before + 1);
        assertThat(auditCount("return-payment-request")).isEqualTo(1);
        TableSession session = tx.execute(s -> em.find(TableSession.class, f.session().getId()));
        assertThat(session.getStatus()).isEqualTo(TableSession.Status.OPEN);
        Bill reloaded = tx.execute(s -> em.find(Bill.class, f.bill().getId()));
        assertThat(reloaded.getRequestedAt()).isNull();

        call(HttpMethod.POST, path, cashierToken, "{\"billVersion\":%d,\"reason\":\"r\"}".formatted(version(f)))
                .andExpect(status().isConflict());
        call(HttpMethod.POST, path, cashierToken, "{\"reason\":\"r\"}").andExpect(status().isBadRequest());
    }

    @Test
    void returnPaymentRequestIsBlockedByPayments() throws Exception {
        Fixture f = billFixture();
        openShift(cashierToken, 0);
        pay(f, 1000, "c6-ret");
        call(HttpMethod.POST, "/bills/" + f.bill().getId() + "/return-payment-request", cashierToken,
                "{\"billVersion\":%d,\"reason\":\"r\"}".formatted(version(f))).andExpect(status().isConflict());
    }
}
