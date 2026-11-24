package com.vertex.restosphere;

import static com.vertex.restosphere.common.util.Values.id;
import static com.vertex.restosphere.common.util.Values.map;
import static org.junit.jupiter.api.Assertions.*;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.common.enums.TableStatus;
import com.vertex.restosphere.entity.*;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.service.PayosGateway;
import com.vertex.restosphere.service.PayosPaymentQueries;
import com.vertex.restosphere.service.PayosPayments;
import com.vertex.restosphere.service.ReservationWorkflow;
import com.vertex.restosphere.service.WorkspaceStore;
import com.vertex.restosphere.service.impl.FakePayosGateway;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

/** Deposit expiry asks payOS first; "check again" and the public status read the real state. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class DepositExpiryAndStatusTests {
    @Autowired WorkspaceStore db;
    @Autowired ReservationWorkflow reservations;
    @Autowired PayosPayments payosPayments;
    @Autowired PayosPaymentQueries queries;
    @MockitoSpyBean PayosGateway gateway;

    Area area;

    Employee user(Role role) {
        var u = new Employee();
        u.setEmail(UUID.randomUUID() + "@test.example");
        u.setFullName(role.name());
        u.setPasswordHash("unused");
        u.setRole(role);
        return db.save(u);
    }

    Reservation booking(String phone) {
        area = new Area();
        area.setName("Expiry " + UUID.randomUUID());
        db.save(area);
        var t = new RestaurantTable();
        t.setArea(area);
        t.setCode("E-" + UUID.randomUUID().toString().substring(0, 8));
        t.setCapacity(4);
        t.setStatus(TableStatus.AVAILABLE);
        db.save(t);
        var category = new MenuCategory();
        category.setName("Cat " + UUID.randomUUID());
        db.save(category);
        var menu = new MenuItem();
        menu.setName("Cua");
        menu.setCategory(category);
        menu.setPrice(new BigDecimal("100000"));
        db.save(menu);
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
                                List.of(map("dishId", id(menu), "quantity", 1))));
        return db.query(
                        Reservation.class,
                        "from Reservation r where r.code = ?1",
                        view.get("code").toString())
                .getFirst();
    }

    void expire(Reservation r) {
        r.setDepositDueAt(Instant.now().minusSeconds(1));
    }

    @Test
    void anUnpaidBookingIsCancelledAndItsLinkCancelledSoNobodyCanPayLate() {
        var r = booking("0908100001");
        var link = r.livePayosPayment();
        expire(r);

        reservations.expireNoShows();

        assertEquals(Reservation.Status.CANCELLED, r.getStatus());
        assertEquals(Payment.Status.CANCELLED, link.getStatus());
        assertEquals(
                PayosGateway.LinkStatus.CANCELLED,
                gateway.status(link.getPayosOrderCode()).status());
    }

    @Test
    void ifPayosSaysItWasPaidTheBookingIsConfirmedNotCancelled() {
        var r = booking("0908100002");
        var link = r.livePayosPayment();
        ((FakePayosGateway) gateway).markPaid(link.getPayosOrderCode());
        expire(r);

        reservations.expireNoShows();

        assertEquals(Reservation.Status.CONFIRMED, r.getStatus());
        assertEquals(Payment.Status.COMPLETED, link.getStatus());
        assertFalse(r.awaitingDeposit());
    }

    @Test
    void ifPayosCannotBeReachedTheBookingIsKeptForTheNextRun() {
        var r = booking("0908100003");
        expire(r);
        org.mockito.Mockito.doThrow(new ApiException(HttpStatus.BAD_GATEWAY, "down"))
                .when(gateway)
                .status(org.mockito.ArgumentMatchers.anyLong());

        reservations.expireNoShows();

        assertEquals(Reservation.Status.PENDING, r.getStatus());
        assertNotNull(r.livePayosPayment());
    }

    @Test
    void moneyThatArrivesAfterTheCancellationIsNeverRevivedAndTheManagerIsTold() {
        var r = booking("0908100004");
        var link = r.livePayosPayment();
        expire(r);
        reservations.expireNoShows();
        assertEquals(Reservation.Status.CANCELLED, r.getStatus());

        var outcome =
                payosPayments.markPaid(
                        link.getPayosOrderCode(), link.getAmount().longValue(), "LATE");

        assertEquals(PayosPayments.Outcome.LATE_PAYMENT, outcome);
        assertEquals(Reservation.Status.CANCELLED, r.getStatus());
        assertEquals(Payment.Status.CANCELLED, link.getStatus());
        assertFalse(
                db.query(
                                Notification.class,
                                "from Notification n where n.kind = ?1 and n.audienceRole = ?2",
                                "LATE_PAYMENT",
                                Role.MANAGER)
                        .isEmpty());
    }

    @Test
    void refreshTurnsAPaidLinkIntoConfirmationAndAnyStaffRoleMayAsk() {
        var r = booking("0908100005");
        var link = r.livePayosPayment();
        var reception = user(Role.RECEPTIONIST);
        assertEquals("PENDING", queries.refresh(reception.getId(), link.getId()).status());

        ((FakePayosGateway) gateway).markPaid(link.getPayosOrderCode());

        assertEquals("SUCCEEDED", queries.refresh(reception.getId(), link.getId()).status());
        assertEquals(Reservation.Status.CONFIRMED, r.getStatus());
        assertThrows(
                ApiException.class,
                () -> queries.refresh(user(Role.KITCHEN).getId(), link.getId()));
    }

    @Test
    void refreshRejectsPaymentsThatAreNotPayos() {
        var r = booking("0908100006");
        var staff = user(Role.CASHIER);
        var shift = new CashierShift();
        shift.setCashier(staff);
        shift.setOpenedAt(Instant.now());
        shift.setOpeningCash(BigDecimal.ZERO);
        db.save(shift);
        var cash = new Payment();
        cash.setReservation(r);
        cash.setCashierShift(shift);
        cash.setAmount(BigDecimal.TEN);
        cash.setMethod(Payment.Method.CASH);
        cash.setStatus(Payment.Status.PENDING);
        db.save(cash);
        assertThrows(ApiException.class, () -> queries.refresh(staff.getId(), cash.getId()));
    }

    @Test
    void publicStatusNeedsTheBookingsOwnCodeAndPhoneAndReadsTheRealState() {
        var r = booking("0908100007");
        var link = r.livePayosPayment();

        var pending = queries.publicStatus(link.getPayosOrderCode(), r.getCode(), "0908100007");
        assertFalse(pending.paid());
        assertEquals("PENDING", pending.status());

        assertThrows(
                ApiException.class,
                () -> queries.publicStatus(link.getPayosOrderCode(), r.getCode(), "0999999999"));
        assertThrows(
                ApiException.class,
                () -> queries.publicStatus(link.getPayosOrderCode(), "RS-OTHER", "0908100007"));
        assertThrows(ApiException.class, () -> queries.publicStatus(1L, r.getCode(), "0908100007"));

        ((FakePayosGateway) gateway).markPaid(link.getPayosOrderCode());
        var paid = queries.publicStatus(link.getPayosOrderCode(), r.getCode(), "0908100007");
        assertTrue(paid.paid());
        assertEquals("CONFIRMED", paid.reservationStatus());
    }
}
