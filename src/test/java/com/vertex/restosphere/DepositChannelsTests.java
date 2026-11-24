package com.vertex.restosphere;

import static com.vertex.restosphere.common.util.Values.id;
import static com.vertex.restosphere.common.util.Values.map;
import static org.junit.jupiter.api.Assertions.*;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.common.enums.TableStatus;
import com.vertex.restosphere.entity.*;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.service.PayosGateway;
import com.vertex.restosphere.service.PayosPayments;
import com.vertex.restosphere.service.ReservationWorkflow;
import com.vertex.restosphere.service.WorkspaceService;
import com.vertex.restosphere.service.WorkspaceStore;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

/** Deposits by channel: online, hotline, counter transfer, counter cash, and payOS being down. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class DepositChannelsTests {
    @Autowired WorkspaceStore db;
    @Autowired WorkspaceService workspace;
    @Autowired ReservationWorkflow reservations;
    @Autowired PayosPayments payosPayments;
    @MockitoSpyBean PayosGateway gateway;

    Employee reception;
    Area area;

    Employee user(Role role) {
        var u = new Employee();
        u.setEmail(UUID.randomUUID() + "@test.example");
        u.setFullName(role.name());
        u.setPasswordHash("unused");
        u.setRole(role);
        return db.save(u);
    }

    void setUp() {
        reception = user(Role.RECEPTIONIST);
        area = new Area();
        area.setName("Deposit " + UUID.randomUUID());
        db.save(area);
        var t = new RestaurantTable();
        t.setArea(area);
        t.setCode("D-" + UUID.randomUUID().toString().substring(0, 8));
        t.setCapacity(4);
        t.setStatus(TableStatus.AVAILABLE);
        db.save(t);
    }

    Map<String, Object> items() {
        var category = new MenuCategory();
        category.setName("Cat " + UUID.randomUUID());
        db.save(category);
        var menu = new MenuItem();
        menu.setName("Cua");
        menu.setCategory(category);
        menu.setPrice(new BigDecimal("200000"));
        db.save(menu);
        return map("dishId", id(menu), "quantity", 1);
    }

    Reservation byCode(String code) {
        return db.query(Reservation.class, "from Reservation r where r.code = ?1", code).getFirst();
    }

    Reservation online(String phone) {
        var view =
                reservations.createPublic(
                        map(
                                "name",
                                "Khach",
                                "phone",
                                phone,
                                "startsAt",
                                Instant.now().plusSeconds(86400).toString(),
                                "guests",
                                2,
                                "areaId",
                                id(area),
                                "items",
                                List.of(items())));
        return byCode(view.get("code").toString());
    }

    Reservation staffBooking(boolean counter) {
        var input =
                map(
                        "customer",
                        map("name", "Khach", "phone", "0908000001"),
                        "startsAt",
                        Instant.now().plusSeconds(86400).toString(),
                        "guests",
                        2,
                        "areaId",
                        id(area),
                        "preorder",
                        List.of(map("menuItemId", items().get("dishId"), "quantity", 1)),
                        "notes",
                        "");
        if (counter) input.put("counter", true);
        String id =
                workspace
                        .command(id(reception), map("type", "create", "input", input))
                        .get("reservationId")
                        .toString();
        return db.get(Reservation.class, id);
    }

    Payment live(Reservation r) {
        return r.livePayosPayment();
    }

    @Test
    void onlineBookingGetsAPayosLinkAndConfirmsItselfWhenPaid() {
        setUp();
        var r = online("0908000010");
        var link = live(r);
        assertNotNull(link);
        assertEquals(Payment.Method.PAYOS, link.getMethod());
        assertNull(link.getBill());
        assertNull(link.getCashierShift());
        assertEquals(r.getDepositAmount(), link.getAmount());

        payosPayments.simulatePaid(link.getPayosOrderCode());

        assertEquals(Reservation.Status.CONFIRMED, r.getStatus());
        assertNotNull(reservations.assignment(r), "a table is held once the deposit is in");
        assertNull(live(r));
        assertFalse(r.awaitingDeposit());
    }

    @Test
    void whenPayosIsDownOnlineBookingIsRefusedWithTheHotlineAndLeavesNothingBehind() {
        setUp();
        db.lock().setPhone("02812345678");
        org.mockito.Mockito.doThrow(
                        new ApiException(HttpStatus.BAD_GATEWAY, "Không kết nối được payOS"))
                .when(gateway)
                .createLink(org.mockito.ArgumentMatchers.any());

        var e =
                assertThrows(
                        ApiException.class,
                        () ->
                                reservations.createPublic(
                                        map(
                                                "name",
                                                "Khach",
                                                "phone",
                                                "0908000099",
                                                "startsAt",
                                                Instant.now().plusSeconds(86400).toString(),
                                                "guests",
                                                2,
                                                "areaId",
                                                id(area),
                                                "items",
                                                List.of(items()))));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatus());
        assertTrue(e.getMessage().contains("02812345678"), "the hotline is shown to the guest");
    }

    @Test
    void whenPayosIsDownAStaffBookingIsStillSavedSoCashCanBeTaken() {
        setUp();
        org.mockito.Mockito.doThrow(
                        new ApiException(HttpStatus.BAD_GATEWAY, "Không kết nối được payOS"))
                .when(gateway)
                .createLink(org.mockito.ArgumentMatchers.any());

        var r = staffBooking(false);

        assertEquals(Reservation.Status.PENDING, r.getStatus());
        assertNull(live(r));
        assertTrue(r.awaitingDeposit());
    }

    @Test
    void hotlineBookingGetsALinkForTheReceptionistToCopy() {
        setUp();
        var r = staffBooking(false);
        assertEquals(Reservation.Source.PHONE, r.getSource());
        assertEquals(Reservation.Status.PENDING, r.getStatus());
        assertNotNull(live(r));
        assertNotNull(live(r).getCheckoutUrl());
    }

    @Test
    void counterBookingHasNoLinkUntilThePayButtonIsPressedThenReusesIt() {
        setUp();
        var r = staffBooking(true);
        assertEquals(Reservation.Source.WALK_IN, r.getSource());
        assertEquals(Reservation.Status.PENDING, r.getStatus());
        assertNull(live(r));

        workspace.command(id(reception), map("type", "deposit-link", "id", id(r)));
        var first = live(r);
        assertNotNull(first);
        workspace.command(id(reception), map("type", "deposit-link", "id", id(r)));
        assertEquals(first.getId(), live(r).getId(), "pressing again must not create a second QR");

        payosPayments.simulatePaid(first.getPayosOrderCode());
        assertEquals(Reservation.Status.CONFIRMED, r.getStatus());
        assertThrows(
                ApiException.class,
                () -> workspace.command(id(reception), map("type", "deposit-link", "id", id(r))));
    }

    @Test
    void cashDepositIsHandedToTheCashierAndDropsAnyPayosLink() {
        setUp();
        var r = staffBooking(false);
        var link = live(r);
        assertNotNull(link);

        workspace.command(id(reception), map("type", "deposit-cash", "id", id(r)));

        assertNotNull(r.getDepositCashRequestedAt());
        assertNull(live(r));
        assertEquals(Payment.Status.CANCELLED, link.getStatus());
        assertEquals(
                PayosGateway.LinkStatus.CANCELLED,
                gateway.status(link.getPayosOrderCode()).status());
        assertEquals(Reservation.Status.PENDING, r.getStatus());
        assertTrue(
                r.getPayments().stream().noneMatch(p -> p.getStatus() == Payment.Status.COMPLETED),
                "reception never takes money");
    }

    @Test
    void aPaidBookingNeedsNoFurtherDepositCommand() {
        setUp();
        var r = staffBooking(true);
        workspace.command(id(reception), map("type", "deposit-link", "id", id(r)));
        payosPayments.simulatePaid(live(r).getPayosOrderCode());
        assertThrows(
                ApiException.class,
                () -> workspace.command(id(reception), map("type", "deposit-cash", "id", id(r))));
    }

    private static com.vertex.restosphere.config.PayosProperties unconfigured() {
        return new com.vertex.restosphere.config.PayosProperties(
                false,
                "not-configured",
                "not-configured",
                "not-configured",
                "http://localhost/r",
                "http://localhost/c",
                java.time.Duration.ofMinutes(30),
                java.time.Duration.ofSeconds(5));
    }
}
