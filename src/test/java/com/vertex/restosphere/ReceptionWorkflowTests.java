package com.vertex.restosphere;

import static com.vertex.restosphere.common.util.Values.*;
import static org.junit.jupiter.api.Assertions.*;

import com.vertex.restosphere.common.enums.*;
import com.vertex.restosphere.entity.*;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.service.*;
import com.vertex.restosphere.service.impl.ReservationWorkflowImpl;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

/** Reception (mobile staff workspace) and public customer reservation workflows. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ReceptionWorkflowTests {
    @Autowired WorkspaceStore db;
    @Autowired WorkspaceService workspace;
    @Autowired ReservationWorkflow reservations;
    @Autowired MockMvc mvc;

    User user(Role role) {
        var u = new User();
        u.setEmail(UUID.randomUUID() + "@test.example");
        u.setFullName(role.name());
        u.setPasswordHash("unused");
        u.setRole(role);
        return db.save(u);
    }

    Area area(String name) {
        var a = new Area();
        a.setName(name + " " + UUID.randomUUID());
        return db.save(a);
    }

    RestaurantTable table(Area area, int capacity) {
        var t = new RestaurantTable();
        t.setArea(area);
        t.setCode("R-" + UUID.randomUUID().toString().substring(0, 8));
        t.setCapacity(capacity);
        t.setStatus(TableStatus.AVAILABLE);
        return db.save(t);
    }

    Reservation byCode(String code) {
        return db.query(Reservation.class, "from Reservation r where r.code = ?1", code).getFirst();
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> list(Map<String, Object> state, String key) {
        return (List<Map<String, Object>>) state.get(key);
    }

    Map<String, Object> run(User user, Object... args) {
        return workspace.command(id(user), map(args));
    }

    boolean hasKind(User user, String workspaceName, String key, String kind) {
        return list(workspace.load(workspaceName, id(user)), key).stream()
                .anyMatch(n -> kind.equals(n.get("kind")));
    }

    @Test
    void codeLookupCancelAndConfirmAutoAssign() {
        var staff = user(Role.RECEPTIONIST);
        var area = area("Bookings");
        var table = table(area, 4);
        var input =
                map(
                        "name",
                        "Guest",
                        "phone",
                        "0901234567",
                        "startsAt",
                        Instant.now().plusSeconds(7200).toString(),
                        "guests",
                        2,
                        "areaId",
                        id(area),
                        "occasion",
                        "none",
                        "items",
                        List.of());
        var booking = reservations.createPublic(input);
        String code = booking.get("code").toString();
        String day =
                Instant.now()
                        .atZone(ZoneId.of(db.lock().getTimezone()))
                        .format(DateTimeFormatter.ofPattern("ddMMyy"));
        assertTrue(code.matches("RS-" + day + "-\\d{3}"), code);
        int seq = Integer.parseInt(code.substring(10));
        assertEquals(area.getName(), booking.get("areaName"));
        var r = byCode(code);
        reservations.assign(r, id(table));
        var second =
                reservations.create(
                        map(
                                "customer",
                                map("name", "Second", "phone", "0907654321"),
                                "startsAt",
                                r.getStartsAt().toString(),
                                "guests",
                                2,
                                "duration",
                                120,
                                "areaId",
                                id(area)),
                        staff,
                        false,
                        false);
        assertEquals(String.format("RS-%s-%03d", day, seq + 1), second.getCode());
        var later =
                reservations.create(
                        map(
                                "customer",
                                map("name", "Later", "phone", "0907654322"),
                                "startsAt",
                                Instant.now().plusSeconds(5 * 86400).toString(),
                                "guests",
                                2,
                                "areaId",
                                id(area)),
                        staff,
                        false,
                        false);
        assertEquals(
                String.format("RS-%s-%03d", day, seq + 2),
                later.getCode(),
                "code uses creation date, not arrival date");
        assertThrows(ApiException.class, () -> reservations.createPublic(input));
        assertThrows(
                ApiException.class,
                () ->
                        reservations.cancelPublic(
                                map("code", code, "phone", "0907654321", "reason", "x")));
        assertEquals(
                Reservation.Status.CANCELLED,
                reservations
                        .cancelPublic(map("code", code, "phone", "0901234567", "reason", "Busy"))
                        .get("status"));
        assertThrows(
                ApiException.class,
                () ->
                        reservations.cancelPublic(
                                map("code", code, "phone", "0901234567", "reason", "Again")));
        var web = byCode(reservations.createPublic(input).get("code").toString());
        run(staff, "type", "confirm", "id", id(web));
        assertEquals(
                id(table),
                id(reservations.assignment(web).getTable()),
                "confirm auto-assigns a free fitting table");
        assertThrows(ApiException.class, () -> run(staff, "type", "confirm", "id", id(second)));
    }

    @Test
    void receptionWalkInEditCheckInAndNotifications() {
        var reception = user(Role.RECEPTIONIST);
        var waiter = user(Role.WAITER);
        var area = area("Reception");
        var other = area("Other");
        var small = table(area, 2);
        var big = table(area, 6);
        var otherTable = table(other, 4);
        var publicInput =
                map(
                        "name",
                        "Web",
                        "phone",
                        "0901111222",
                        "startsAt",
                        Instant.now().plusSeconds(1800).toString(),
                        "guests",
                        2,
                        "areaId",
                        id(area),
                        "tableId",
                        id(small));
        var web = byCode(reservations.createPublic(publicInput).get("code").toString());
        assertNull(reservations.assignment(web), "public callers must not pick a table");
        assertTrue(hasKind(reception, "staff", "notifications", "NEW_WEB_RESERVATION"));
        run(reception, "type", "assign", "id", id(web), "tableId", id(small));
        var customer = map("name", "Web", "phone", "0901111222");
        run(
                reception,
                "type",
                "edit",
                "id",
                id(web),
                "input",
                map(
                        "customer",
                        customer,
                        "startsAt",
                        web.getStartsAt().toString(),
                        "guests",
                        3,
                        "duration",
                        120,
                        "areaId",
                        id(other),
                        "tableId",
                        id(otherTable)));
        assertEquals(id(otherTable), id(reservations.assignment(web).getTable()));
        run(reception, "type", "confirm", "id", id(web));
        assertThrows(
                ApiException.class,
                () ->
                        run(
                                reception,
                                "type",
                                "edit",
                                "id",
                                id(web),
                                "input",
                                map(
                                        "customer",
                                        customer,
                                        "startsAt",
                                        web.getStartsAt().toString(),
                                        "guests",
                                        3,
                                        "areaId",
                                        id(other))));
        assertThrows(
                ApiException.class,
                () -> run(reception, "type", "request-exception", "id", id(web), "reason", "x"));
        run(reception, "type", "request-exception", "id", id(web), "reason", "VIP window seat");
        assertFalse(
                db.query(
                                Notification.class,
                                "from Notification n where n.audienceRole = ?1 and n.kind = ?2",
                                Role.MANAGER,
                                "APPROVAL")
                        .isEmpty());
        var walkIn =
                map(
                        "customer",
                        map("name", "", "phone", "0903333444"),
                        "startsAt",
                        Instant.now().toString(),
                        "guests",
                        2,
                        "areaId",
                        id(area),
                        "tableId",
                        id(small));
        String walkId =
                run(reception, "type", "create", "walkIn", true, "input", walkIn)
                        .get("reservationId")
                        .toString();
        var w = db.get(Reservation.class, walkId);
        assertEquals(Reservation.Status.ARRIVED, w.getStatus());
        assertEquals("Khách trực tiếp", w.getCustomerName());
        assertEquals(Reservation.Source.WALK_IN, w.getSource());
        assertThrows(
                ApiException.class,
                () -> run(reception, "type", "create", "walkIn", true, "input", walkIn));
        run(reception, "type", "handover", "id", walkId);
        run(reception, "type", "check-in", "id", id(web), "actualGuests", 5);
        assertThrows(ApiException.class, () -> run(reception, "type", "handover", "id", id(web)));
        run(reception, "type", "assign", "id", id(web), "tableId", id(table(other, 6)));
        run(reception, "type", "handover", "id", id(web));
        assertTrue(hasKind(waiter, "staff", "waiterNotifications", "HANDOVER"));
        run(reception, "type", "assign", "id", walkId, "tableId", id(big));
        assertEquals(TableStatus.OCCUPIED, big.getStatus());
        assertEquals(TableStatus.AVAILABLE, small.getStatus());
        assertTrue(hasKind(waiter, "staff", "waiterNotifications", "TABLE_REASSIGNED"));
        publicInput.put("startsAt", Instant.now().plusSeconds(86400).toString());
        String second = reservations.createPublic(publicInput).get("code").toString();
        reservations.cancelPublic(
                map("code", second, "phone", "0901111222", "reason", "Change of plans"));
        assertTrue(hasKind(reception, "staff", "notifications", "RESERVATION_CANCELLED"));
    }

    @Test
    void bufferNoShowHoldAndReceptionRequests() {
        var reception = user(Role.RECEPTIONIST);
        var area = area("Buffer");
        table(area, 4);
        db.lock().setBufferMinutes(30);
        var start = Instant.now().plusSeconds(86400);
        reservations.createPublic(
                map(
                        "name",
                        "A",
                        "phone",
                        "0901000001",
                        "startsAt",
                        start.toString(),
                        "guests",
                        4,
                        "durationMinutes",
                        60,
                        "areaId",
                        id(area)));
        assertThrows(
                ApiException.class,
                () ->
                        reservations.createPublic(
                                map(
                                        "name",
                                        "B",
                                        "phone",
                                        "0901000002",
                                        "startsAt",
                                        start.plusSeconds(4200).toString(),
                                        "guests",
                                        4,
                                        "durationMinutes",
                                        60,
                                        "areaId",
                                        id(area))),
                "inside buffer");
        assertDoesNotThrow(
                () ->
                        reservations.createPublic(
                                map(
                                        "name",
                                        "C",
                                        "phone",
                                        "0901000003",
                                        "startsAt",
                                        start.plusSeconds(5400).toString(),
                                        "guests",
                                        4,
                                        "durationMinutes",
                                        60,
                                        "areaId",
                                        id(area))));
        var late =
                reservations.create(
                        map(
                                "customer",
                                map("name", "Late", "phone", "0901000004"),
                                "startsAt",
                                Instant.now().toString(),
                                "guests",
                                2,
                                "areaId",
                                id(area)),
                        reception,
                        false,
                        false);
        late.setStatus(Reservation.Status.CONFIRMED);
        late.setStartsAt(Instant.now().minusSeconds(3600));
        assertTrue(reservations.expireNoShows() >= 1);
        assertEquals(Reservation.Status.NO_SHOW, late.getStatus());
        db.lock().setHoldMinutes(15);
        db.lock().setNoShowMinutes(30);
        db.lock().setBufferMinutes(0);
        var held =
                reservations.create(
                        map(
                                "customer",
                                map("name", "Held", "phone", "0901000006"),
                                "startsAt",
                                Instant.now().toString(),
                                "guests",
                                2,
                                "areaId",
                                id(area),
                                "tableId",
                                id(table(area, 2))),
                        reception,
                        false,
                        false);
        held.setStatus(Reservation.Status.CONFIRMED);
        held.setStartsAt(Instant.now().minusSeconds(20 * 60));
        reservations.expireNoShows();
        assertEquals(Reservation.Status.CONFIRMED, held.getStatus(), "still waiting until no-show");
        assertNull(reservations.assignment(held), "table released after hold window");
        var pending =
                reservations.create(
                        map(
                                "customer",
                                map("name", "Ex", "phone", "0901000005"),
                                "startsAt",
                                start.plusSeconds(20000).toString(),
                                "guests",
                                1,
                                "areaId",
                                id(area)),
                        reception,
                        false,
                        false);
        run(reception, "type", "request-exception", "id", id(pending), "reason", "Need approval");
        assertTrue(
                list(workspace.load("staff", id(reception)), "requests").stream()
                        .anyMatch(r -> "RESERVATION".equals(r.get("type").toString())));
    }

    @Test
    void openingHoursEarlyCheckInAndHandoverConflicts() {
        assertArrayEquals(
                new int[] {600, 1320},
                ReservationWorkflowImpl.openingMinutes("Mở cửa 10:00 - 22:00 hằng ngày"));
        assertArrayEquals(
                new int[] {1020, 1560}, ReservationWorkflowImpl.openingMinutes("17h00-02h00"));
        assertNull(ReservationWorkflowImpl.openingMinutes("Liên hệ"));
        var reception = user(Role.RECEPTIONIST);
        var area = area("Hours");
        var t = table(area, 4);
        var day = LocalDate.now(ZoneId.of(db.lock().getTimezone())).plusDays(2).toString();
        db.lock().setOpeningHours("10:00 - 22:00");
        assertThrows(
                ApiException.class,
                () -> reservations.createPublic(hoursInput(day, "21:30", area)),
                "ends after closing");
        assertThrows(
                ApiException.class,
                () -> reservations.createPublic(hoursInput(day, "08:00", area)),
                "before opening");
        assertDoesNotThrow(() -> reservations.createPublic(hoursInput(day, "12:00", area)));
        db.lock().setOpeningHours(null);
        var future = staffBooking(reception, area, t, 3 * 3600, 120, "0902000002");
        run(reception, "type", "confirm", "id", id(future));
        assertThrows(
                ApiException.class,
                () -> run(reception, "type", "check-in", "id", id(future), "actualGuests", 2),
                "too early");
        var t2 = table(area, 4);
        var blocker = staffBooking(reception, area, t2, 600, 15, "0902000004");
        run(reception, "type", "confirm", "id", id(blocker));
        var early = staffBooking(reception, area, t2, 3000, 60, "0902000003");
        run(reception, "type", "confirm", "id", id(early));
        run(reception, "type", "check-in", "id", id(early), "actualGuests", 2);
        assertThrows(
                ApiException.class,
                () -> run(reception, "type", "handover", "id", id(early)),
                "seating now would overlap the next booking on this table");
    }

    Map<String, Object> hoursInput(String day, String time, Area area) {
        return map(
                "name",
                "N",
                "phone",
                "0902000001",
                "date",
                day,
                "time",
                time,
                "guests",
                2,
                "areaId",
                id(area));
    }

    Reservation staffBooking(
            User actor, Area area, RestaurantTable t, int inSeconds, int duration, String phone) {
        return reservations.create(
                map(
                        "customer",
                        map("name", "S", "phone", phone),
                        "startsAt",
                        Instant.now().plusSeconds(inSeconds).toString(),
                        "guests",
                        2,
                        "duration",
                        duration,
                        "areaId",
                        id(area),
                        "tableId",
                        id(t)),
                actor,
                false,
                false);
    }

    @Test
    void publicEndpointsAreRateLimited() throws Exception {
        int last = 0;
        for (int i = 0; i < 25; i++) {
            last =
                    mvc.perform(
                                    MockMvcRequestBuilders.post("/api/v1/reservations/lookup")
                                            .with(
                                                    r -> {
                                                        r.setRemoteAddr("10.9.9.9");
                                                        return r;
                                                    })
                                            .contentType("application/json")
                                            .content(
                                                    "{\"code\":\"RS-X\",\"phone\":\"0900000000\"}"))
                            .andReturn()
                            .getResponse()
                            .getStatus();
        }
        assertEquals(429, last);
    }
}
