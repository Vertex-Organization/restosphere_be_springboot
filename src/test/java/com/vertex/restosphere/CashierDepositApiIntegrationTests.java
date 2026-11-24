package com.vertex.restosphere;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.entity.Employee;
import com.vertex.restosphere.entity.Payment;
import com.vertex.restosphere.entity.Reservation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class CashierDepositApiIntegrationTests extends CashierApiTestBase {
    private static final String MANAGER_EMAIL = "d1-manager@restosphere.test";

    @Override
    String prefix() {
        return "d1";
    }

    private Reservation reservation(boolean cashRequested) {
        return reservation(cashRequested, Instant.now().plusSeconds(600));
    }

    private Reservation reservation(boolean cashRequested, Instant dueAt) {
        Reservation r = new Reservation();
        r.setCode("RS-D1-" + UUID.randomUUID().toString().substring(0, 6));
        r.setCustomerName("Khach coc");
        r.setPhone("0909000001");
        r.setGuestCount(2);
        r.setStartsAt(Instant.now().plusSeconds(86400));
        r.setEndsAt(Instant.now().plusSeconds(86400 + 7200));
        r.setSource(Reservation.Source.WALK_IN);
        r.setDepositAmount(new BigDecimal("150000"));
        r.setDepositDueAt(dueAt);
        if (cashRequested) r.setDepositCashRequestedAt(Instant.now());
        return persist(r);
    }

    private String cashJson(long tendered, String key) {
        return "{\"tendered\":%d,\"idempotencyKey\":\"%s\"}".formatted(tendered, key);
    }

    private String path(Reservation r, String kind) {
        return "/reservations/" + r.getId() + "/deposit/" + kind;
    }

    private Reservation reload(Reservation r) {
        return tx.execute(
                s -> {
                    Reservation x = em.find(Reservation.class, r.getId());
                    x.getPayments().size();
                    return x;
                });
    }

    @Test
    void authIsEnforced() throws Exception {
        Reservation r = reservation(true);
        call(HttpMethod.POST, path(r, "cash"), null, cashJson(150000, "k"))
                .andExpect(status().isUnauthorized());
        call(HttpMethod.POST, path(r, "cash"), waiterToken, cashJson(150000, "k"))
                .andExpect(status().isForbidden());
        call(HttpMethod.GET, "/deposits/pending", waiterToken, null)
                .andExpect(status().isForbidden());
    }

    @Test
    void pendingListShowsOnlyBookingsHandedToTheCashierAndStillDue() throws Exception {
        Reservation handed = reservation(true);
        reservation(false);
        reservation(true, Instant.now().minusSeconds(5));

        String body = ok(call(HttpMethod.GET, "/deposits/pending", cashierToken, null));

        java.util.List<String> ids =
                json(body, "$[?(@.reservationId=='%s')].reservationId".formatted(handed.getId()));
        assertThat(ids).containsExactly(handed.getId().toString());
        java.util.List<String> all = json(body, "$[*].reservationId");
        assertThat(all).hasSize(1);
    }

    @Test
    void cashDepositIsServerPricedGoesToTheShiftReturnsChangeAndConfirmsTheBooking()
            throws Exception {
        Reservation r = reservation(true);
        String shiftId = openShift(cashierToken, 0);

        String body =
                created(
                        call(
                                HttpMethod.POST,
                                path(r, "cash"),
                                cashierToken,
                                cashJson(200000, "d1-a")));

        assertThat(this.<String>json(body, "$.method")).isEqualTo("CASH");
        assertThat(this.<String>json(body, "$.status")).isEqualTo("SUCCEEDED");
        assertThat(this.<Number>json(body, "$.amount").longValue()).isEqualTo(150000L);
        assertThat(this.<Number>json(body, "$.change").longValue()).isEqualTo(50000L);
        assertThat(this.<String>json(body, "$.shiftId")).isEqualTo(shiftId);
        assertThat(this.<String>json(body, "$.reservationId")).isEqualTo(r.getId().toString());
        assertThat(this.<Object>json(body, "$.billId")).isNull();
        Reservation after = reload(r);
        assertThat(after.awaitingDeposit()).isFalse();
        assertThat(auditCount("deposit-cash")).isEqualTo(1);

        String list = ok(call(HttpMethod.GET, "/deposits/pending", cashierToken, null));
        assertThat(this.<java.util.List<Object>>json(list, "$[*]")).isEmpty();
    }

    @Test
    void cashDepositNeedsTheShortfallToBeCoveredAnOpenShiftAndTheHandOver() throws Exception {
        Reservation r = reservation(true);
        call(HttpMethod.POST, path(r, "cash"), cashierToken, cashJson(150000, "d1-noshift"))
                .andExpect(status().isConflict());
        openShift(cashierToken, 0);
        call(HttpMethod.POST, path(r, "cash"), cashierToken, cashJson(100000, "d1-short"))
                .andExpect(status().isBadRequest());
        Reservation notHanded = reservation(false);
        call(HttpMethod.POST, path(notHanded, "cash"), cashierToken, cashJson(150000, "d1-no"))
                .andExpect(status().isConflict());
        assertThat(count("select count(p) from Payment p where p.reservation.id = :p0", r.getId()))
                .isZero();
    }

    @Test
    void replayReturnsTheSamePaymentAndNeverChargesTwice() throws Exception {
        Reservation r = reservation(true);
        openShift(cashierToken, 0);
        String first =
                created(
                        call(
                                HttpMethod.POST,
                                path(r, "cash"),
                                cashierToken,
                                cashJson(150000, "d1-r")));
        String again =
                ok(call(HttpMethod.POST, path(r, "cash"), cashierToken, cashJson(150000, "d1-r")));
        assertThat(this.<String>json(again, "$.id")).isEqualTo(this.<String>json(first, "$.id"));
        Reservation other = reservation(true);
        call(HttpMethod.POST, path(other, "cash"), cashierToken, cashJson(150000, "d1-r"))
                .andExpect(status().isConflict());
        assertThat(count("select count(p) from Payment p where p.reservation.id = :p0", r.getId()))
                .isEqualTo(1);
    }

    @Test
    void managerConfirmedTransferWorksWhenPayosIsDownAndBadPasswordsAreAudited() throws Exception {
        saveUser(MANAGER_EMAIL, Role.MANAGER);
        Reservation r = reservation(false);
        openShift(cashierToken, 0);
        String bad =
                ("{\"idempotencyKey\":\"d1-t1\",\"transactionReference\":\"BANK-D1\","
                                + "\"managerEmail\":\"%s\",\"managerPassword\":\"nope\"}")
                        .formatted(MANAGER_EMAIL);
        call(HttpMethod.POST, path(r, "manual-transfer"), cashierToken, bad)
                .andExpect(status().isForbidden());
        assertThat(
                        count(
                                "select count(a) from AuditLog a where a.action = :p0 and a.targetType ="
                                        + " :p1",
                                "manual-transfer-denied",
                                "RESERVATION"))
                .isEqualTo(1);

        String good = bad.replace("nope", PASSWORD).replace("d1-t1", "d1-t2");
        String body =
                created(call(HttpMethod.POST, path(r, "manual-transfer"), cashierToken, good));

        assertThat(this.<String>json(body, "$.method")).isEqualTo("MANUAL_TRANSFER");
        assertThat(this.<String>json(body, "$.status")).isEqualTo("SUCCEEDED");
        assertThat(this.<String>json(body, "$.bankReference")).isEqualTo("BANK-D1");
        Employee manager = users.findByEmail(MANAGER_EMAIL).orElseThrow();
        assertThat(this.<String>json(body, "$.verifiedBy")).isEqualTo(manager.getId().toString());
        Payment saved =
                reload(r).getPayments().stream()
                        .filter(p -> p.getStatus() == Payment.Status.COMPLETED)
                        .findFirst()
                        .orElseThrow();
        assertThat(saved.getBill()).isNull();
        assertThat(saved.getCashierShift()).isNotNull();
        assertThat(reload(r).awaitingDeposit()).isFalse();
    }
}
