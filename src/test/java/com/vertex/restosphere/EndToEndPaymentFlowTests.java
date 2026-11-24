package com.vertex.restosphere;

import static com.vertex.restosphere.common.util.Values.id;
import static com.vertex.restosphere.common.util.Values.map;
import static org.junit.jupiter.api.Assertions.*;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.common.enums.TableStatus;
import com.vertex.restosphere.dto.request.CashierOpenShiftRequest;
import com.vertex.restosphere.dto.request.CashierPayosLinkRequest;
import com.vertex.restosphere.dto.request.KitchenItemRef;
import com.vertex.restosphere.entity.*;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.service.CashierPaymentService;
import com.vertex.restosphere.service.CashierShiftService;
import com.vertex.restosphere.service.KitchenService;
import com.vertex.restosphere.service.PayosPayments;
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
import org.springframework.transaction.annotation.Transactional;

/** Whole flows through the services, paying with the payOS simulator instead of real money. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class EndToEndPaymentFlowTests {
    @Autowired WorkspaceStore db;
    @Autowired WorkspaceService workspace;
    @Autowired KitchenService kitchen;
    @Autowired CashierShiftService shifts;
    @Autowired CashierPaymentService payments;
    @Autowired PayosPayments payos;

    Employee user(Role role) {
        var u = new Employee();
        u.setEmail(UUID.randomUUID() + "@test.example");
        u.setFullName(role.name());
        u.setPasswordHash("unused");
        u.setRole(role);
        return db.save(u);
    }

    Area area() {
        var a = new Area();
        a.setName("E2E " + UUID.randomUUID());
        return db.save(a);
    }

    RestaurantTable table(Area area, int capacity) {
        var t = new RestaurantTable();
        t.setArea(area);
        t.setCode("E-" + UUID.randomUUID().toString().substring(0, 8));
        t.setCapacity(capacity);
        t.setStatus(TableStatus.AVAILABLE);
        return db.save(t);
    }

    MenuItem dish(String price) {
        var category = new MenuCategory();
        category.setName("Cat " + UUID.randomUUID());
        db.save(category);
        var menu = new MenuItem();
        menu.setName("Dish");
        menu.setCategory(category);
        menu.setPrice(new BigDecimal(price));
        return db.save(menu);
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> run(Employee who, Object... args) {
        return (Map<String, Object>) workspace.command(id(who), map(args)).get("data");
    }

    TableSession sessionOf(Reservation r) {
        return db.query(TableSession.class, "from TableSession s where s.reservation=?1", r)
                .getFirst();
    }

    @Test
    void draftOrderToKitchenToBillToPayosPaymentLeavesTheTableToBeCleaned() {
        var reception = user(Role.RECEPTIONIST);
        var waiter = user(Role.WAITER);
        var cook = user(Role.KITCHEN);
        var cashier = user(Role.CASHIER);
        var area = area();
        var table = table(area, 4);
        var menu = dish("100000");

        var input =
                map(
                        "customer",
                        map("name", "Khach", "phone", "0909100001"),
                        "startsAt",
                        Instant.now().plusSeconds(3600).toString(),
                        "guests",
                        2,
                        "areaId",
                        id(area),
                        "tableId",
                        id(table),
                        "notes",
                        "");
        String reservation =
                workspace
                        .command(id(reception), map("type", "create", "input", input))
                        .get("reservationId")
                        .toString();
        run(reception, "type", "confirm", "id", reservation);
        run(reception, "type", "check-in", "id", reservation, "actualGuests", 2);
        run(reception, "type", "handover", "id", reservation);
        var session = sessionOf(db.get(Reservation.class, reservation));
        run(waiter, "type", "accept", "sessionId", id(session));

        run(waiter, "type", "add-draft", "sessionId", id(session), "menuItemId", id(menu));
        var drafts =
                db.query(
                        OrderItem.class,
                        "from OrderItem i where i.order.session=?1 and i.status=?2",
                        session,
                        OrderItem.Status.DRAFT);
        assertEquals(1, drafts.size(), "the dish sits in a DRAFT cart first");
        assertEquals(
                new BigDecimal("0"),
                session.getBill().getSubtotal(),
                "a draft is not on the bill yet");

        run(waiter, "type", "submit", "sessionId", id(session));
        var item =
                db.query(OrderItem.class, "from OrderItem i where i.order.session=?1", session)
                        .getFirst();
        assertEquals(OrderItem.Status.SUBMITTED, item.getStatus());
        var ref = new KitchenItemRef(id(item), item.getVersion());
        kitchen.start(cook.getId(), List.of(ref));
        kitchen.complete(
                cook.getId(),
                List.of(
                        new KitchenItemRef(
                                id(item), db.get(OrderItem.class, id(item)).getVersion())));
        run(waiter, "type", "serve", "sessionId", id(session), "itemId", id(item));
        run(waiter, "type", "request-payment", "sessionId", id(session));

        var bill = session.getBill();
        assertEquals(new BigDecimal("100000"), bill.getTotal());
        shifts.open(cashier.getId(), new CashierOpenShiftRequest(BigDecimal.ZERO, null));
        var link =
                payments.createPayosLink(
                        cashier.getId(),
                        bill.getId(),
                        new CashierPayosLinkRequest(bill.getVersion()));
        assertTrue(link.created());
        assertEquals(Bill.Status.OPEN, bill.getStatus(), "a link alone does not pay the bill");

        var code = link.payment().orderCode();
        assertEquals(PayosPayments.Outcome.COMPLETED, payos.simulatePaid(code));

        assertEquals(Bill.Status.PAID, bill.getStatus());
        assertEquals(TableSession.Status.CLOSED, session.getStatus());
        assertEquals(TableStatus.CLEANING, table.getStatus());
        assertEquals(
                Reservation.Status.COMPLETED, db.get(Reservation.class, reservation).getStatus());
        assertEquals(PayosPayments.Outcome.ALREADY_COMPLETED, payos.simulatePaid(code));

        var walkIn =
                map(
                        "customer",
                        map("name", "Khach 2", "phone", "0909100002"),
                        "startsAt",
                        Instant.now().toString(),
                        "guests",
                        2,
                        "areaId",
                        id(area),
                        "tableId",
                        id(table));
        assertThrows(
                ApiException.class,
                () -> run(reception, "type", "create", "walkIn", true, "input", walkIn),
                "reception cannot seat anyone at a table still being cleaned");

        run(waiter, "type", "clean", "sessionId", id(session), "confirmed", true);
        assertEquals(TableStatus.AVAILABLE, table.getStatus());
    }

    @Test
    void depositThenMultiTableCheckInGivesOneSharedBillThatCarriesTheDeposit() {
        var reception = user(Role.RECEPTIONIST);
        var area = area();
        var first = table(area, 4);
        var second = table(area, 4);
        var menu = dish("200000");

        var input =
                map(
                        "customer",
                        map("name", "Doan", "phone", "0909200001"),
                        "startsAt",
                        Instant.now().plusSeconds(3600).toString(),
                        "guests",
                        6,
                        "areaId",
                        id(area),
                        "tableIds",
                        List.of(id(first), id(second)),
                        "preorder",
                        List.of(map("menuItemId", id(menu), "quantity", 1)),
                        "notes",
                        "");
        String reservationId =
                workspace
                        .command(id(reception), map("type", "create", "input", input))
                        .get("reservationId")
                        .toString();
        var reservation = db.get(Reservation.class, reservationId);
        assertTrue(reservation.awaitingDeposit());
        var link = reservation.livePayosPayment();
        assertNotNull(link, "a hotline booking gets its payOS link at once");

        assertEquals(PayosPayments.Outcome.COMPLETED, payos.simulatePaid(link.getPayosOrderCode()));
        assertEquals(Reservation.Status.CONFIRMED, reservation.getStatus());
        assertNull(link.getBill(), "until check-in the deposit belongs to the reservation");

        run(reception, "type", "check-in", "id", reservationId, "actualGuests", 6);
        run(reception, "type", "handover", "id", reservationId);

        var sessions =
                db.query(
                        TableSession.class,
                        "from TableSession s where s.reservation=?1",
                        reservation);
        assertEquals(2, sessions.size());
        var bill = sessions.getFirst().getBill();
        assertTrue(sessions.stream().allMatch(s -> s.getBill().getId().equals(bill.getId())));
        assertEquals(6, sessions.stream().mapToInt(TableSession::getGuestCount).sum());
        assertEquals(
                bill.getId(), link.getBill().getId(), "the deposit now rides on the shared bill");
        assertEquals(
                reservation.getDepositAmount(),
                db.query(
                                Payment.class,
                                "from Payment p where p.bill=?1 and p.status=?2",
                                bill,
                                Payment.Status.COMPLETED)
                        .getFirst()
                        .getAmount());
        assertEquals(TableStatus.OCCUPIED, first.getStatus());
        assertEquals(TableStatus.OCCUPIED, second.getStatus());
    }
}
