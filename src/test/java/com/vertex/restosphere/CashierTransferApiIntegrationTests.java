package com.vertex.restosphere;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.entity.Bill;
import com.vertex.restosphere.entity.Employee;
import com.vertex.restosphere.entity.Payment;
import com.vertex.restosphere.entity.RestaurantSettings;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class CashierTransferApiIntegrationTests extends CashierApiTestBase {
    private String oldBank;

    @Override
    String prefix() {
        return "c4";
    }

    @BeforeEach
    void configureBank() {
        tx.executeWithoutResult(
                t -> {
                    RestaurantSettings s =
                            settings.findBySingletonKey(1)
                                    .orElseGet(
                                            () -> {
                                                RestaurantSettings created =
                                                        new RestaurantSettings();
                                                created.setName("RestoSphere");
                                                return settings.save(created);
                                            });
                    oldBank = s.getBankAccountNumber();
                    s.setBankAccountNumber("0123456789");
                });
    }

    @AfterEach
    void restoreBank() {
        tx.executeWithoutResult(
                t ->
                        settings.findBySingletonKey(1)
                                .ifPresent(s -> s.setBankAccountNumber(oldBank)));
    }

    private String json(long version, long amount, String key) {
        return "{\"billVersion\":%d,\"amount\":%d,\"idempotencyKey\":\"%s\"}"
                .formatted(version, amount, key);
    }

    private String transferPath(Fixture f) {
        return "/bills/" + f.bill().getId() + "/payments/transfer";
    }

    private long version(Fixture f) {
        return tx.execute(s -> em.find(Bill.class, f.bill().getId()).getVersion());
    }

    private Bill.Status billStatus(Fixture f) {
        return tx.execute(s -> em.find(Bill.class, f.bill().getId()).getStatus());
    }

    private long waiterNotifications() {
        return count(
                "select count(n) from Notification n where n.kind = 'PAYMENT' and n.audienceRole ="
                        + " :p0",
                Role.WAITER);
    }

    private String createTransfer(Fixture f, long amount, String key) throws Exception {
        String body =
                created(
                        call(
                                HttpMethod.POST,
                                transferPath(f),
                                cashierToken,
                                json(version(f), amount, key)));
        return this.<String>json(body, "$.id");
    }

    @Test
    void authIsEnforced() throws Exception {
        Fixture f = billFixture();
        String id = UUID.randomUUID().toString();
        call(HttpMethod.POST, transferPath(f), null, json(0, 1, "k"))
                .andExpect(status().isUnauthorized());
        call(HttpMethod.POST, transferPath(f), waiterToken, json(0, 1, "k"))
                .andExpect(status().isForbidden());
        call(
                        HttpMethod.POST,
                        "/payments/" + id + "/confirm",
                        waiterToken,
                        "{\"bankReference\":\"x\"}")
                .andExpect(status().isForbidden());
        call(HttpMethod.POST, "/payments/" + id + "/cancel", null, null)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createTransferIsPendingWithTransferContentAndDoesNotSettle() throws Exception {
        Fixture f = billFixture();
        openShift(cashierToken, 0);
        long before = revision();
        String body =
                created(
                        call(
                                HttpMethod.POST,
                                transferPath(f),
                                cashierToken,
                                json(version(f), 100000, "c4-new")));
        assertThat(this.<String>json(body, "$.status")).isEqualTo("PENDING");
        assertThat(this.<String>json(body, "$.method")).isEqualTo("TRANSFER");
        assertThat(this.<String>json(body, "$.transferContent")).startsWith("RS");
        assertThat(billStatus(f)).isEqualTo(Bill.Status.OPEN);
        assertThat(revision()).isEqualTo(before + 1);
        assertThat(auditCount("create-transfer")).isEqualTo(1);
        assertThat(waiterNotifications()).isZero();

        call(HttpMethod.POST, transferPath(f), cashierToken, json(version(f), 1000, "c4-other"))
                .andExpect(status().isConflict());
        String replay =
                ok(call(HttpMethod.POST, transferPath(f), cashierToken, json(0, 100000, "c4-new")));
        assertThat(this.<String>json(replay, "$.id")).isEqualTo(this.<String>json(body, "$.id"));
        call(HttpMethod.POST, transferPath(f), cashierToken, json(0, 90000, "c4-new"))
                .andExpect(status().isConflict());
    }

    @Test
    void createTransferNeedsBankAccountShiftAndValidAmount() throws Exception {
        Fixture f = billFixture();
        call(HttpMethod.POST, transferPath(f), cashierToken, json(version(f), 1000, "c4-noshift"))
                .andExpect(status().isConflict());
        openShift(cashierToken, 0);
        call(HttpMethod.POST, transferPath(f), cashierToken, json(version(f), 100001, "c4-over"))
                .andExpect(status().isBadRequest());
        call(HttpMethod.POST, transferPath(f), cashierToken, json(version(f) + 3, 1000, "c4-ver"))
                .andExpect(status().isConflict());
        tx.executeWithoutResult(
                t -> settings.findBySingletonKey(1).orElseThrow().setBankAccountNumber(""));
        call(HttpMethod.POST, transferPath(f), cashierToken, json(version(f), 1000, "c4-nobank"))
                .andExpect(status().isConflict());
        assertThat(count("select count(p) from Payment p where p.bill.id = :p0", f.bill().getId()))
                .isZero();
    }

    @Test
    void confirmSettlesBillNotifiesWaiterAndRejectsReusedReference() throws Exception {
        Fixture f = billFixture();
        String shiftId = openShift(cashierToken, 0);
        String paymentId = createTransfer(f, 100000, "c4-confirm");
        long before = revision();

        call(
                        HttpMethod.POST,
                        "/payments/" + paymentId + "/confirm",
                        cashierToken,
                        "{\"bankReference\":\"  \"}")
                .andExpect(status().isBadRequest());
        String body =
                ok(
                        call(
                                HttpMethod.POST,
                                "/payments/" + paymentId + "/confirm",
                                cashierToken,
                                "{\"bankReference\":\"BANK-001\"}"));
        assertThat(this.<String>json(body, "$.status")).isEqualTo("SUCCEEDED");
        assertThat(this.<String>json(body, "$.bankReference")).isEqualTo("BANK-001");
        assertThat(this.<String>json(body, "$.verifiedBy")).isEqualTo(cashier.getId().toString());
        assertThat(billStatus(f)).isEqualTo(Bill.Status.PAID);
        assertThat(revision()).isEqualTo(before + 1);
        assertThat(auditCount("confirm-transfer")).isEqualTo(1);
        assertThat(waiterNotifications()).isEqualTo(1);

        call(
                        HttpMethod.POST,
                        "/payments/" + paymentId + "/confirm",
                        cashierToken,
                        "{\"bankReference\":\"BANK-002\"}")
                .andExpect(status().isConflict());

        Fixture g = billFixture();
        String second = createTransfer(g, 50000, "c4-confirm2");
        call(
                        HttpMethod.POST,
                        "/payments/" + second + "/confirm",
                        cashierToken,
                        "{\"bankReference\":\"BANK-001\"}")
                .andExpect(status().isConflict());
        assertThat(shiftId).isNotBlank();
    }

    @Test
    void partialConfirmKeepsBillOpenWithoutNotification() throws Exception {
        Fixture f = billFixture();
        openShift(cashierToken, 0);
        String paymentId = createTransfer(f, 40000, "c4-part");
        ok(
                call(
                        HttpMethod.POST,
                        "/payments/" + paymentId + "/confirm",
                        cashierToken,
                        "{\"bankReference\":\"BANK-PART\"}"));
        assertThat(billStatus(f)).isEqualTo(Bill.Status.OPEN);
        assertThat(waiterNotifications()).isZero();
    }

    @Test
    void cancelWithdrawsPendingTransferAndUnblocksNewPayment() throws Exception {
        Fixture f = billFixture();
        String shiftId = openShift(cashierToken, 0);
        String paymentId = createTransfer(f, 100000, "c4-cancel");
        long before = revision();

        String body =
                ok(call(HttpMethod.POST, "/payments/" + paymentId + "/cancel", cashierToken, null));
        assertThat(this.<String>json(body, "$.status")).isEqualTo("CANCELLED");
        assertThat(revision()).isEqualTo(before + 1);
        assertThat(auditCount("cancel-transfer")).isEqualTo(1);
        call(HttpMethod.POST, "/payments/" + paymentId + "/cancel", cashierToken, null)
                .andExpect(status().isConflict());
        created(
                call(
                        HttpMethod.POST,
                        "/bills/" + f.bill().getId() + "/payments/cash",
                        cashierToken,
                        "{\"billVersion\":%d,\"amount\":1000,\"tendered\":1000,\"idempotencyKey\":\"c4-cash\"}"
                                .formatted(version(f))));
        assertThat(shiftId).isNotBlank();
    }

    @Test
    void closeShiftIsBlockedWhileTransferPending() throws Exception {
        Fixture f = billFixture();
        String shiftId = openShift(cashierToken, 0);
        String paymentId = createTransfer(f, 100000, "c4-close");
        call(HttpMethod.POST, "/shifts/" + shiftId + "/close", cashierToken, "{\"countedCash\":0}")
                .andExpect(status().isConflict());
        ok(call(HttpMethod.POST, "/payments/" + paymentId + "/cancel", cashierToken, null));
        ok(
                call(
                        HttpMethod.POST,
                        "/shifts/" + shiftId + "/close",
                        cashierToken,
                        "{\"countedCash\":0}"));
    }

    @Test
    void transferOfAnotherCashierOrNonTransferOrUnknownIsRejected() throws Exception {
        Fixture f = billFixture();
        openShift(cashierToken, 0);
        String paymentId = createTransfer(f, 100000, "c4-owner");
        Employee other = saveUser("c4-cashier2@restosphere.test", Role.CASHIER);
        String otherToken = bearer(other.getEmail());
        call(
                        HttpMethod.POST,
                        "/payments/" + paymentId + "/confirm",
                        otherToken,
                        "{\"bankReference\":\"X-1\"}")
                .andExpect(status().isConflict());
        call(HttpMethod.POST, "/payments/" + paymentId + "/cancel", otherToken, null)
                .andExpect(status().isConflict());
        call(HttpMethod.POST, "/payments/" + UUID.randomUUID() + "/cancel", cashierToken, null)
                .andExpect(status().isNotFound());
        call(HttpMethod.POST, "/payments/not-a-uuid/cancel", cashierToken, null)
                .andExpect(status().isBadRequest());

        Fixture g = billFixture();
        ok(call(HttpMethod.POST, "/payments/" + paymentId + "/cancel", cashierToken, null));
        String cash =
                created(
                        call(
                                HttpMethod.POST,
                                "/bills/" + g.bill().getId() + "/payments/cash",
                                cashierToken,
                                "{\"billVersion\":%d,\"amount\":1000,\"tendered\":1000,\"idempotencyKey\":\"c4-cashonly\"}"
                                        .formatted(version(g))));
        call(
                        HttpMethod.POST,
                        "/payments/" + this.<String>json(cash, "$.id") + "/cancel",
                        cashierToken,
                        null)
                .andExpect(status().isConflict());
        assertThat(
                        count(
                                "select count(p) from Payment p where p.status = :p0 and p.bill.id"
                                        + " = :p1",
                                Payment.Status.CANCELLED,
                                f.bill().getId()))
                .isEqualTo(1);
    }
}
