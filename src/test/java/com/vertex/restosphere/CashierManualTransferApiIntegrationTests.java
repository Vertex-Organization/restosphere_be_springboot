package com.vertex.restosphere;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.entity.Bill;
import com.vertex.restosphere.entity.Employee;
import com.vertex.restosphere.entity.RestaurantSettings;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class CashierManualTransferApiIntegrationTests extends CashierApiTestBase {
    private static final String MANAGER_EMAIL = "c4-manager@restosphere.test";
    private String oldBank;
    private Employee manager;

    @Override
    String prefix() {
        return "c4";
    }

    @BeforeEach
    void configureBankAndManager() {
        manager = saveUser(MANAGER_EMAIL, Role.MANAGER);
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

    private String json(long version, long amount, String key, String ref, String password) {
        return ("{\"billVersion\":%d,\"amount\":%d,\"idempotencyKey\":\"%s\","
                        + "\"transactionReference\":\"%s\",\"proofImageUrl\":\"https://img.example/p.jpg\","
                        + "\"managerEmail\":\"%s\",\"managerPassword\":\"%s\"}")
                .formatted(version, amount, key, ref, MANAGER_EMAIL, password);
    }

    private String json(long version, long amount, String key, String ref) {
        return json(version, amount, key, ref, PASSWORD);
    }

    private String path(Fixture f) {
        return "/bills/" + f.bill().getId() + "/payments/manual-transfer";
    }

    private long version(Fixture f) {
        return tx.execute(s -> em.find(Bill.class, f.bill().getId()).getVersion());
    }

    private Bill.Status billStatus(Fixture f) {
        return tx.execute(s -> em.find(Bill.class, f.bill().getId()).getStatus());
    }

    @Test
    void authIsEnforced() throws Exception {
        Fixture f = billFixture();
        call(HttpMethod.POST, path(f), null, json(0, 1, "k", "r"))
                .andExpect(status().isUnauthorized());
        call(HttpMethod.POST, path(f), waiterToken, json(0, 1, "k", "r"))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerConfirmationCompletesThePaymentImmediately() throws Exception {
        Fixture f = billFixture();
        openShift(cashierToken, 0);
        long before = revision();
        String body =
                created(
                        call(
                                HttpMethod.POST,
                                path(f),
                                cashierToken,
                                json(version(f), 100000, "c4-ok", "BANK-001")));
        assertThat(this.<String>json(body, "$.status")).isEqualTo("SUCCEEDED");
        assertThat(this.<String>json(body, "$.method")).isEqualTo("MANUAL_TRANSFER");
        assertThat(this.<String>json(body, "$.bankReference")).isEqualTo("BANK-001");
        assertThat(this.<String>json(body, "$.proofImageUrl"))
                .isEqualTo("https://img.example/p.jpg");
        assertThat(this.<String>json(body, "$.verifiedBy")).isEqualTo(manager.getId().toString());
        assertThat(this.<String>json(body, "$.createdBy")).isEqualTo(cashier.getId().toString());
        assertThat(billStatus(f)).isEqualTo(Bill.Status.PAID);
        assertThat(revision()).isEqualTo(before + 1);
        assertThat(auditCount("pay-manual-transfer")).isEqualTo(1);
        assertThat(
                        count(
                                "select count(a) from AuditLog a where a.action = :p0 and a.actor.id = :p1",
                                "manual-transfer-confirmed",
                                manager.getId()))
                .isEqualTo(1);

        String replay =
                ok(
                        call(
                                HttpMethod.POST,
                                path(f),
                                cashierToken,
                                json(0, 100000, "c4-ok", "BANK-001")));
        assertThat(this.<String>json(replay, "$.id")).isEqualTo(this.<String>json(body, "$.id"));
    }

    @Test
    void wrongManagerPasswordOrNonManagerIsForbiddenAndRecordsNothing() throws Exception {
        Fixture f = billFixture();
        openShift(cashierToken, 0);
        call(
                        HttpMethod.POST,
                        path(f),
                        cashierToken,
                        json(version(f), 1000, "c4-bad", "B-1", "nope"))
                .andExpect(status().isForbidden());
        String asCashier =
                json(version(f), 1000, "c4-cash", "B-2").replace(MANAGER_EMAIL, cashier.getEmail());
        call(HttpMethod.POST, path(f), cashierToken, asCashier).andExpect(status().isForbidden());
        assertThat(count("select count(p) from Payment p where p.bill.id = :p0", f.bill().getId()))
                .isZero();
        assertThat(
                        count(
                                "select count(a) from AuditLog a where a.action = :p0",
                                "manual-transfer-denied"))
                .isEqualTo(2);
    }

    @Test
    void tooManyWrongManagerPasswordsLockTheManagerAccountAndAreAudited() throws Exception {
        String email = "c4-locked@restosphere.test";
        saveUser(email, Role.MANAGER);
        Fixture f = billFixture();
        openShift(cashierToken, 0);
        String body =
                json(version(f), 1000, "c4-lock", "B-L", "nope").replace(MANAGER_EMAIL, email);
        for (int i = 0; i < 10; i++) {
            call(HttpMethod.POST, path(f), cashierToken, body).andExpect(status().isForbidden());
        }
        call(HttpMethod.POST, path(f), cashierToken, body).andExpect(status().isTooManyRequests());
        String correct = body.replace("nope", PASSWORD);
        call(HttpMethod.POST, path(f), cashierToken, correct)
                .andExpect(status().isTooManyRequests());
        assertThat(
                        count(
                                "select count(a) from AuditLog a where a.action = :p0",
                                "manual-transfer-denied"))
                .isEqualTo(10);
        assertThat(
                        count(
                                "select count(a) from AuditLog a where a.action = :p0",
                                "manual-transfer-blocked"))
                .isEqualTo(2);
        assertThat(count("select count(p) from Payment p where p.bill.id = :p0", f.bill().getId()))
                .isZero();
    }

    @Test
    void needsBankAccountShiftAndValidAmountAndUniqueReference() throws Exception {
        Fixture f = billFixture();
        call(HttpMethod.POST, path(f), cashierToken, json(version(f), 1000, "c4-noshift", "B-3"))
                .andExpect(status().isConflict());
        openShift(cashierToken, 0);
        call(HttpMethod.POST, path(f), cashierToken, json(version(f), 100001, "c4-over", "B-4"))
                .andExpect(status().isBadRequest());
        call(HttpMethod.POST, path(f), cashierToken, json(version(f) + 3, 1000, "c4-ver", "B-5"))
                .andExpect(status().isConflict());
        created(
                call(
                        HttpMethod.POST,
                        path(f),
                        cashierToken,
                        json(version(f), 40000, "c4-first", "BANK-DUP")));
        call(HttpMethod.POST, path(f), cashierToken, json(version(f), 1000, "c4-dup", "BANK-DUP"))
                .andExpect(status().isConflict());
        assertThat(billStatus(f)).isEqualTo(Bill.Status.OPEN);
        tx.executeWithoutResult(
                t -> settings.findBySingletonKey(1).orElseThrow().setBankAccountNumber(""));
        call(HttpMethod.POST, path(f), cashierToken, json(version(f), 1000, "c4-nobank", "B-6"))
                .andExpect(status().isConflict());
    }

    @Test
    void closeShiftIsNotBlockedByCompletedManualTransfers() throws Exception {
        Fixture f = billFixture();
        String shiftId = openShift(cashierToken, 0);
        created(
                call(
                        HttpMethod.POST,
                        path(f),
                        cashierToken,
                        json(version(f), 100000, "c4-close", "BANK-CLOSE")));
        ok(
                call(
                        HttpMethod.POST,
                        "/shifts/" + shiftId + "/close",
                        cashierToken,
                        "{\"countedCash\":0}"));
        assertThat(UUID.fromString(shiftId)).isNotNull();
    }
}
