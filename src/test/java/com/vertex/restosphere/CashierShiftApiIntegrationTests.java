package com.vertex.restosphere;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.entity.Employee;
import com.vertex.restosphere.entity.Shift;
import com.vertex.restosphere.entity.ShiftAssignment;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class CashierShiftApiIntegrationTests extends CashierApiTestBase {
    @Override
    String prefix() {
        return "c1";
    }

    private Shift shift(Instant start, Instant end) {
        Shift s = new Shift();
        s.setName("c1 shift");
        s.setStartsAt(start);
        s.setEndsAt(end);
        s.setMinimumStaff(1);
        return persist(s);
    }

    private ShiftAssignment assign(Shift s, Employee u) {
        ShiftAssignment a = new ShiftAssignment();
        a.setShift(s);
        a.setEmployee(u);
        return persist(a);
    }

    @Test
    void authIsEnforced() throws Exception {
        for (String p : List.of("/shifts", "/scheduled-shifts")) {
            call(HttpMethod.GET, p, null, null).andExpect(status().isUnauthorized());
            call(HttpMethod.GET, p, waiterToken, null).andExpect(status().isForbidden());
        }
        call(HttpMethod.POST, "/shifts/open", waiterToken, "{\"openingFloat\":0}")
                .andExpect(status().isForbidden());
        call(HttpMethod.POST, "/shifts/open", null, "{\"openingFloat\":0}")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void openShiftCreatesItBumpsRevisionAndWritesAudit() throws Exception {
        long before = revision();
        String body =
                created(
                        call(
                                HttpMethod.POST,
                                "/shifts/open",
                                cashierToken,
                                "{\"openingFloat\":500000}"));
        assertThat(this.<String>json(body, "$.status")).isEqualTo("OPEN");
        assertThat(this.<Number>json(body, "$.openingFloat").longValue()).isEqualTo(500000L);
        assertThat(this.<String>json(body, "$.cashierId")).isEqualTo(cashier.getId().toString());
        assertThat(this.<String>json(body, "$.code")).startsWith("S-");
        assertThat(revision()).isEqualTo(before + 1);
        assertThat(auditCount("open-shift")).isEqualTo(1);

        String list = ok(call(HttpMethod.GET, "/shifts", cashierToken, null));
        assertThat(
                        this.<List<Object>>json(
                                list, "$[?(@.cashierId=='%s')]".formatted(cashier.getId())))
                .hasSize(1);
    }

    @Test
    void openShiftRejectsSecondOpenShiftAndBadAmounts() throws Exception {
        call(HttpMethod.POST, "/shifts/open", cashierToken, "{}")
                .andExpect(status().isBadRequest());
        call(HttpMethod.POST, "/shifts/open", cashierToken, "{\"openingFloat\":-1}")
                .andExpect(status().isBadRequest());
        call(HttpMethod.POST, "/shifts/open", cashierToken, "{\"openingFloat\":10.5}")
                .andExpect(status().isBadRequest());
        assertThat(
                        count(
                                "select count(c) from CashierShift c where c.cashier.id = :p0",
                                cashier.getId()))
                .isZero();
        openShift(cashierToken, 0);
        call(HttpMethod.POST, "/shifts/open", cashierToken, "{\"openingFloat\":0}")
                .andExpect(status().isConflict());
    }

    @Test
    void openShiftWithScheduledShiftValidatesAssignmentAndWindow() throws Exception {
        Instant now = Instant.now();
        Shift current = shift(now.minus(1, ChronoUnit.HOURS), now.plus(2, ChronoUnit.HOURS));
        Shift soon = shift(now.plus(10, ChronoUnit.MINUTES), now.plus(4, ChronoUnit.HOURS));
        Shift far = shift(now.plus(5, ChronoUnit.HOURS), now.plus(9, ChronoUnit.HOURS));
        Shift unassigned = shift(now.minus(1, ChronoUnit.HOURS), now.plus(2, ChronoUnit.HOURS));
        assign(current, cashier);
        assign(soon, cashier);
        assign(far, cashier);

        call(
                        HttpMethod.POST,
                        "/shifts/open",
                        cashierToken,
                        "{\"openingFloat\":0,\"scheduledShiftId\":\"%s\"}"
                                .formatted(unassigned.getId()))
                .andExpect(status().isBadRequest());
        call(
                        HttpMethod.POST,
                        "/shifts/open",
                        cashierToken,
                        "{\"openingFloat\":0,\"scheduledShiftId\":\"%s\"}".formatted(far.getId()))
                .andExpect(status().isConflict());
        call(
                        HttpMethod.POST,
                        "/shifts/open",
                        cashierToken,
                        "{\"openingFloat\":0,\"scheduledShiftId\":\"not-a-uuid\"}")
                .andExpect(status().isBadRequest());
        call(
                        HttpMethod.POST,
                        "/shifts/open",
                        cashierToken,
                        "{\"openingFloat\":0,\"scheduledShiftId\":\"%s\"}"
                                .formatted(java.util.UUID.randomUUID()))
                .andExpect(status().isNotFound());

        String body =
                created(
                        call(
                                HttpMethod.POST,
                                "/shifts/open",
                                cashierToken,
                                "{\"openingFloat\":0,\"scheduledShiftId\":\"%s\"}"
                                        .formatted(soon.getId())));
        assertThat(this.<String>json(body, "$.scheduledShiftId"))
                .isEqualTo(soon.getId().toString());
    }

    @Test
    void scheduledShiftsListAssignmentsOfEveryCashier() throws Exception {
        Employee other = saveUser("c1-cashier2@restosphere.test", Role.CASHIER);
        Instant now = Instant.now();
        Shift s = shift(now.minus(1, ChronoUnit.HOURS), now.plus(2, ChronoUnit.HOURS));
        assign(s, cashier);
        assign(s, other);
        assign(s, waiter);

        String body = ok(call(HttpMethod.GET, "/scheduled-shifts", cashierToken, null));
        List<String> cashiers = json(body, "$[?(@.id=='%s')].cashierId".formatted(s.getId()));
        assertThat(cashiers)
                .containsExactlyInAnyOrder(cashier.getId().toString(), other.getId().toString());
    }

    @Test
    void closeShiftComputesExpectedCashAndRejectsInvalidStates() throws Exception {
        String shiftId = openShift(cashierToken, 200000);
        long before = revision();
        call(HttpMethod.POST, "/shifts/" + shiftId + "/close", cashierToken, "{\"note\":\"x\"}")
                .andExpect(status().isBadRequest());
        call(HttpMethod.POST, "/shifts/" + shiftId + "/close", cashierToken, "{\"countedCash\":-5}")
                .andExpect(status().isBadRequest());
        assertThat(revision()).isEqualTo(before);

        String body =
                ok(
                        call(
                                HttpMethod.POST,
                                "/shifts/" + shiftId + "/close",
                                cashierToken,
                                "{\"countedCash\":195000,\"note\":\"short 5k\"}"));
        assertThat(this.<String>json(body, "$.status")).isEqualTo("CLOSED");
        assertThat(this.<Number>json(body, "$.expectedCash").longValue()).isEqualTo(200000L);
        assertThat(this.<Number>json(body, "$.countedCash").longValue()).isEqualTo(195000L);
        assertThat(this.<String>json(body, "$.closingNote")).isEqualTo("short 5k");
        assertThat(revision()).isEqualTo(before + 1);
        assertThat(auditCount("close-shift")).isEqualTo(1);

        call(HttpMethod.POST, "/shifts/" + shiftId + "/close", cashierToken, "{\"countedCash\":0}")
                .andExpect(status().isConflict());
        call(
                        HttpMethod.POST,
                        "/shifts/" + java.util.UUID.randomUUID() + "/close",
                        cashierToken,
                        "{\"countedCash\":0}")
                .andExpect(status().isNotFound());
    }

    @Test
    void closeShiftOfAnotherCashierIsConflict() throws Exception {
        Employee other = saveUser("c1-cashier3@restosphere.test", Role.CASHIER);
        String otherToken = bearer(other.getEmail());
        String shiftId = openShift(otherToken, 0);
        call(HttpMethod.POST, "/shifts/" + shiftId + "/close", cashierToken, "{\"countedCash\":0}")
                .andExpect(status().isConflict());
    }
}
