package com.vertex.restosphere;

import static com.vertex.restosphere.common.util.Values.id;
import static com.vertex.restosphere.common.util.Values.map;
import static org.junit.jupiter.api.Assertions.*;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.common.enums.TableStatus;
import com.vertex.restosphere.entity.*;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.service.BillingWorkflow;
import com.vertex.restosphere.service.TableSessionService;
import com.vertex.restosphere.service.WorkspaceService;
import com.vertex.restosphere.service.WorkspaceStore;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/** A booking spread over several tables: one session per table, one close when the bill is paid. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class MultiTableReservationTests {
    @Autowired WorkspaceStore db;
    @Autowired WorkspaceService workspace;
    @Autowired BillingWorkflow billing;
    @Autowired TableSessionService sessionService;

    Employee reception;
    Area area;
    RestaurantTable first;
    RestaurantTable second;

    Employee user(Role role) {
        var u = new Employee();
        u.setEmail(UUID.randomUUID() + "@test.example");
        u.setFullName(role.name());
        u.setPasswordHash("unused");
        u.setRole(role);
        return db.save(u);
    }

    RestaurantTable table(int capacity) {
        var t = new RestaurantTable();
        t.setArea(area);
        t.setCode("M-" + UUID.randomUUID().toString().substring(0, 8));
        t.setCapacity(capacity);
        t.setStatus(TableStatus.AVAILABLE);
        return db.save(t);
    }

    void run(Object... args) {
        workspace.command(id(reception), map(args));
    }

    Reservation seatedBooking(int guests) {
        reception = user(Role.RECEPTIONIST);
        area = new Area();
        area.setName("Multi " + UUID.randomUUID());
        db.save(area);
        first = table(4);
        second = table(4);
        var input =
                map(
                        "customer",
                        map("name", "Group", "phone", "0907000001"),
                        "startsAt",
                        Instant.now().plusSeconds(3600).toString(),
                        "guests",
                        guests,
                        "duration",
                        120,
                        "areaId",
                        id(area),
                        "tableIds",
                        List.of(id(first), id(second)),
                        "notes",
                        "");
        String reservationId =
                workspace
                        .command(id(reception), map("type", "create", "input", input))
                        .get("reservationId")
                        .toString();
        run("type", "confirm", "id", reservationId);
        run("type", "check-in", "id", reservationId, "actualGuests", guests);
        return db.get(Reservation.class, reservationId);
    }

    List<TableSession> sessionsOf(Reservation r) {
        return db.query(
                TableSession.class,
                "from TableSession s where s.reservation=?1 order by s.startedAt",
                r);
    }

    Payment deposit(Reservation r) {
        var p = new Payment();
        p.setReservation(r);
        p.setAmount(new BigDecimal("50000"));
        p.setMethod(Payment.Method.PAYOS);
        p.setStatus(Payment.Status.COMPLETED);
        p.setPaidAt(Instant.now());
        db.save(p);
        r.getPayments().add(p);
        return p;
    }

    void pay(Bill bill, String amount) {
        bill.setSubtotal(new BigDecimal(amount));
        bill.setTotal(new BigDecimal(amount));
        var p = new Payment();
        p.setBill(bill);
        p.setAmount(new BigDecimal(amount));
        p.setMethod(Payment.Method.PAYOS);
        p.setStatus(Payment.Status.COMPLETED);
        p.setPaidAt(Instant.now());
        db.save(p);
        db.flush();
        billing.settle(bill);
    }

    @Test
    void handoverOpensOneSessionPerTableAndTheDepositFollowsTheFirstBill() {
        var r = seatedBooking(6);
        var deposit = deposit(r);

        run("type", "handover", "id", id(r));

        var sessions = sessionsOf(r);
        assertEquals(2, sessions.size());
        assertEquals(Reservation.Status.SEATED, r.getStatus());
        assertEquals(
                List.of(first.getId(), second.getId()),
                sessions.stream().map(s -> s.getTable().getId()).toList());
        assertEquals(6, sessions.stream().mapToInt(TableSession::getGuestCount).sum());
        assertTrue(sessions.stream().allMatch(s -> s.getGuestCount() >= 1));
        assertNotEquals(sessions.get(0).getBill().getId(), sessions.get(1).getBill().getId());
        assertEquals(sessions.get(0).getBill().getId(), deposit.getBill().getId());
        assertEquals(TableStatus.OCCUPIED, first.getStatus());
        assertEquals(TableStatus.OCCUPIED, second.getStatus());
    }

    @Test
    void handoverNeedsEveryAssignedTableToBeFree() {
        var r = seatedBooking(6);
        second.setStatus(TableStatus.CLEANING);

        assertThrows(ApiException.class, () -> run("type", "handover", "id", id(r)));

        assertTrue(sessionsOf(r).isEmpty());
        assertEquals(Reservation.Status.ARRIVED, r.getStatus());
        assertEquals(TableStatus.AVAILABLE, first.getStatus());
    }

    @Test
    void payingAMergedBillClosesEverySessionAndFreesEveryTable() {
        var r = seatedBooking(6);
        run("type", "handover", "id", id(r));
        var sessions = sessionsOf(r);
        sessionService.merge(reception.getId(), sessions.get(0).getId(), sessions.get(1).getId());
        var bill = sessions.get(0).getBill();
        assertEquals(2, bill.getSessions().size());

        pay(bill, "100000");

        assertEquals(Bill.Status.PAID, bill.getStatus());
        for (var s : sessions) {
            assertEquals(TableSession.Status.CLOSED, s.getStatus());
            assertNotNull(s.getEndedAt());
            assertEquals(TableStatus.AVAILABLE, s.getTable().getStatus());
        }
        assertEquals(Reservation.Status.COMPLETED, r.getStatus());
    }

    @Test
    void payingOneOfTwoSeparateBillsKeepsTheOtherTableAndTheBookingOpen() {
        var r = seatedBooking(6);
        run("type", "handover", "id", id(r));
        var sessions = sessionsOf(r);

        pay(sessions.get(0).getBill(), "100000");

        assertEquals(TableSession.Status.CLOSED, sessions.get(0).getStatus());
        assertEquals(TableStatus.AVAILABLE, sessions.get(0).getTable().getStatus());
        assertEquals(TableSession.Status.OPEN, sessions.get(1).getStatus());
        assertEquals(TableStatus.OCCUPIED, sessions.get(1).getTable().getStatus());
        assertEquals(Reservation.Status.SEATED, r.getStatus());

        pay(sessions.get(1).getBill(), "80000");

        assertEquals(Reservation.Status.COMPLETED, r.getStatus());
        assertEquals(TableStatus.AVAILABLE, sessions.get(1).getTable().getStatus());
    }
}
