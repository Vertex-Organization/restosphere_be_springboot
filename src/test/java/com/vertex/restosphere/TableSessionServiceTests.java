package com.vertex.restosphere;

import static org.junit.jupiter.api.Assertions.*;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.common.enums.TableStatus;
import com.vertex.restosphere.common.enums.PriceUnit;
import com.vertex.restosphere.dto.request.SplitItemRequest;
import com.vertex.restosphere.dto.request.SplitSessionRequest;
import com.vertex.restosphere.entity.*;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.service.BillingWorkflow;
import com.vertex.restosphere.service.TableSessionService;
import com.vertex.restosphere.service.WorkspaceStore;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

/** Merging and splitting table sessions, including every rule that blocks them. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class TableSessionServiceTests {
    @Autowired WorkspaceStore db;
    @Autowired BillingWorkflow billing;
    @Autowired TableSessionService service;

    Employee waiter;
    Area area;

    Employee user(Role role) {
        var u = new Employee();
        u.setEmail(UUID.randomUUID() + "@test.example");
        u.setFullName(role.name());
        u.setPasswordHash("unused");
        u.setRole(role);
        return db.save(u);
    }

    Employee ownWaiter() {
        waiter = waiter == null ? user(Role.WAITER) : waiter;
        return waiter;
    }

    RestaurantTable table(TableStatus status) {
        if (area == null) {
            area = new Area();
            area.setName("Sessions " + UUID.randomUUID());
            db.save(area);
        }
        var t = new RestaurantTable();
        t.setArea(area);
        t.setCode("S-" + UUID.randomUUID().toString().substring(0, 8));
        t.setCapacity(4);
        t.setStatus(status);
        return db.save(t);
    }

    TableSession session(RestaurantTable table) {
        ownWaiter();
        var bill = billing.open();
        var s = new TableSession();
        s.setBill(bill);
        bill.getSessions().add(s);
        s.setTable(table);
        s.setWaiter(waiter);
        s.setGuestCount(2);
        s.setStartedAt(Instant.now());
        table.setStatus(TableStatus.OCCUPIED);
        return db.save(s);
    }

    OrderItem dish(TableSession s, String price, int round) {
        var category = new MenuCategory();
        category.setName("Cat " + UUID.randomUUID());
        db.save(category);
        var menu = new MenuItem();
        menu.setName("Dish");
        menu.setCategory(category);
        menu.setPrice(new BigDecimal(price));
        db.save(menu);
        var o = new Order();
        o.setSession(s);
        o.setWaiter(waiter);
        o.setStatus(Order.Status.SUBMITTED);
        o.setRoundNumber(round);
        o.setSubmittedAt(Instant.now());
        db.save(o);
        var i = new OrderItem();
        i.setOrder(o);
        i.setMenuItem(menu);
        i.setItemName("Dish");
        i.setUnit("portion");
        i.setQuantity(BigDecimal.ONE);
        i.setUnitPrice(new BigDecimal(price));
        i.setStatus(OrderItem.Status.SERVED);
        db.save(i);
        billing.recalculate(s.getBill());
        return i;
    }

    @Test
    void mergeSharesOneBillAndVoidsTheOldOne() {
        var staff = user(Role.RECEPTIONIST);
        var a = session(table(TableStatus.AVAILABLE));
        var b = session(table(TableStatus.AVAILABLE));
        dish(a, "100000", 1);
        dish(b, "50000", 1);
        var oldBill = b.getBill();

        var result = service.merge(staff.getId(), a.getId(), b.getId());

        assertEquals(a.getBill().getId(), b.getBill().getId());
        assertEquals(result.billId(), a.getBill().getId());
        assertEquals(Bill.Status.VOIDED, oldBill.getStatus());
        assertEquals(new BigDecimal("150000"), a.getBill().getTotal());
        assertEquals(2, a.getBill().getSessions().size());
    }

    @Test
    void splitMovesChosenItemsToANewBillAtAnotherFreeTable() {
        var staff = ownWaiter();
        var a = session(table(TableStatus.AVAILABLE));
        var keep = dish(a, "100000", 1);
        var move = dish(a, "40000", 2);
        var free = table(TableStatus.AVAILABLE);

        var result =
                service.split(staff.getId(), a.getId(), free.getId(), whole(move), 1);

        assertNotEquals(a.getBill().getId(), result.billId());
        assertEquals(new BigDecimal("100000"), a.getBill().getTotal());
        assertEquals(TableStatus.OCCUPIED, free.getStatus());
        assertEquals(result.sessionId(), move.getOrder().getSession().getId());
        assertEquals(a.getId(), keep.getOrder().getSession().getId());
        var fresh = db.get(TableSession.class, result.sessionId().toString());
        assertEquals(new BigDecimal("40000"), fresh.getBill().getTotal());
        assertEquals(free.getId(), fresh.getTable().getId());
        assertEquals(1, fresh.getGuestCount());
        assertEquals(1, a.getGuestCount());
    }

    OrderItem bulkDish(TableSession s, String price, int quantity, int round) {
        var i = dish(s, price, round);
        i.setQuantity(new BigDecimal(quantity));
        i.setNote("no onion");
        billing.recalculate(s.getBill());
        return i;
    }

    List<OrderItem> itemsOf(TableSession s) {
        return db.query(OrderItem.class, "from OrderItem i where i.order.session=?1", s);
    }

    List<SplitItemRequest> whole(OrderItem item) {
        return List.of(part(item, null));
    }

    SplitItemRequest part(OrderItem item, String quantity) {
        return new SplitItemRequest(item.getId(), quantity == null ? null : new BigDecimal(quantity));
    }

    @Test
    void splitMovesPartOfAQuantityIntoANewLine() {
        var staff = ownWaiter();
        var a = session(table(TableStatus.AVAILABLE));
        var line = bulkDish(a, "100000", 4, 1);
        var free = table(TableStatus.AVAILABLE);

        var result =
                service.split(
                        staff.getId(), a.getId(), free.getId(), List.of(part(line, "2")), 1);

        assertEquals(0, new BigDecimal("2").compareTo(line.getQuantity()));
        assertEquals(a.getId(), line.getOrder().getSession().getId());
        var fresh = db.get(TableSession.class, result.sessionId().toString());
        var copy = itemsOf(fresh).get(0);
        assertNotEquals(line.getId(), copy.getId());
        assertEquals(0, new BigDecimal("2").compareTo(copy.getQuantity()));
        assertEquals(line.getItemName(), copy.getItemName());
        assertEquals(line.getUnitPrice(), copy.getUnitPrice());
        assertEquals(line.getUnit(), copy.getUnit());
        assertEquals(line.getStatus(), copy.getStatus());
        assertEquals("no onion", copy.getNote());
        assertEquals(new BigDecimal("200000"), a.getBill().getTotal());
        assertEquals(new BigDecimal("200000"), fresh.getBill().getTotal());
    }

    @Test
    void splitWithItemsMovesAWholeLineWhenQuantityMatches() {
        var staff = ownWaiter();
        var a = session(table(TableStatus.AVAILABLE));
        dish(a, "100000", 1);
        var line = bulkDish(a, "40000", 3, 2);
        var free = table(TableStatus.AVAILABLE);

        var result =
                service.split(
                        staff.getId(), a.getId(), free.getId(), List.of(part(line, "3")), 1);

        assertEquals(result.sessionId(), line.getOrder().getSession().getId());
        assertEquals(0, new BigDecimal("3").compareTo(line.getQuantity()));
        var fresh = db.get(TableSession.class, result.sessionId().toString());
        assertEquals(1, itemsOf(fresh).size());
        assertEquals(new BigDecimal("120000"), fresh.getBill().getTotal());
        assertEquals(new BigDecimal("100000"), a.getBill().getTotal());
    }

    @Test
    void splitWithoutQuantityMovesTheWholeLine() {
        var staff = ownWaiter();
        var a = session(table(TableStatus.AVAILABLE));
        var line = bulkDish(a, "40000", 3, 2);
        var free = table(TableStatus.AVAILABLE);

        var result =
                service.split(
                        staff.getId(), a.getId(), free.getId(), List.of(part(line, null)), 1);

        assertEquals(result.sessionId(), line.getOrder().getSession().getId());
        assertEquals(0, new BigDecimal("3").compareTo(line.getQuantity()));
    }

    @Test
    void splitRejectsAnInvalidQuantityWith400() {
        var staff = ownWaiter();
        var a = session(table(TableStatus.AVAILABLE));
        var line = bulkDish(a, "100000", 4, 1);
        var free = table(TableStatus.AVAILABLE);
        for (var quantity : List.of("0", "-1", "5", "1.5")) {
            var error =
                    assertThrows(
                            ApiException.class,
                            () ->
                                    service.split(
                                            staff.getId(),
                                            a.getId(),
                                            free.getId(),
                                            List.of(part(line, quantity)),
                                            1),
                            quantity);
            assertEquals(HttpStatus.BAD_REQUEST, error.getStatus(), quantity);
        }
        assertEquals(0, new BigDecimal("4").compareTo(line.getQuantity()));
        assertEquals(TableStatus.AVAILABLE, free.getStatus());
    }

    @Test
    void splitCanOnlyMoveAWholeLineOfAWeighedDish() {
        var staff = ownWaiter();
        var a = session(table(TableStatus.AVAILABLE));
        var line = dish(a, "100000", 1);
        line.getMenuItem().setPriceUnit(PriceUnit.KG);
        line.setQuantity(new BigDecimal("2.500"));
        var free = table(TableStatus.AVAILABLE);

        var error =
                assertThrows(
                        ApiException.class,
                        () ->
                                service.split(
                                        staff.getId(),
                                        a.getId(),
                                        free.getId(),
                                        List.of(part(line, "1")),
                                        1));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());

        var result =
                service.split(
                        staff.getId(), a.getId(), free.getId(), List.of(part(line, "2.5")), 1);
        assertEquals(result.sessionId(), line.getOrder().getSession().getId());
    }

    @Test
    void splitRequestUsesItemsOrLegacyItemIdsAndNeedsOne() {
        var target = UUID.randomUUID();
        var none = new SplitSessionRequest(target, null, null, 1);
        var error = assertThrows(ApiException.class, none::lines);
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        var legacy = new SplitSessionRequest(target, List.of(UUID.randomUUID()), null, 1);
        assertNull(legacy.lines().get(0).quantity());
    }

    @Test
    void aWaiterCanOnlyMergeOrSplitSessionsTheyServe() {
        var mine = session(table(TableStatus.AVAILABLE));
        var item = dish(mine, "100000", 1);
        var free = table(TableStatus.AVAILABLE);
        var otherWaiter = user(Role.WAITER);
        var foreign = session(table(TableStatus.AVAILABLE));
        foreign.setWaiter(otherWaiter);

        var merge =
                assertThrows(
                        ApiException.class,
                        () -> service.merge(waiter.getId(), mine.getId(), foreign.getId()));
        assertEquals(HttpStatus.FORBIDDEN, merge.getStatus());
        var split =
                assertThrows(
                        ApiException.class,
                        () ->
                                service.split(
                                        otherWaiter.getId(),
                                        mine.getId(),
                                        free.getId(),
                                        whole(item),
                                        1));
        assertEquals(HttpStatus.FORBIDDEN, split.getStatus());
        assertEquals(TableStatus.AVAILABLE, free.getStatus());
    }

    @Test
    void waiterCannotMergeASharedBillContainingAnotherWaitersSession() {
        var target = session(table(TableStatus.AVAILABLE));
        var source = session(table(TableStatus.AVAILABLE));
        var foreign = session(table(TableStatus.AVAILABLE));
        foreign.setWaiter(user(Role.WAITER));
        foreign.getBill().getSessions().remove(foreign);
        foreign.setBill(source.getBill());
        source.getBill().getSessions().add(foreign);

        var error =
                assertThrows(
                        ApiException.class,
                        () -> service.merge(waiter.getId(), target.getId(), source.getId()));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        assertNotEquals(target.getBill().getId(), source.getBill().getId());
    }

    @Test
    void otherRolesAreNotLimitedToTheirOwnSessions() {
        for (var role : List.of(Role.RECEPTIONIST, Role.CASHIER, Role.MANAGER, Role.ADMIN)) {
            var a = session(table(TableStatus.AVAILABLE));
            var b = session(table(TableStatus.AVAILABLE));
            b.setWaiter(user(Role.WAITER));
            var staff = user(role);
            assertDoesNotThrow(
                    () -> service.merge(staff.getId(), a.getId(), b.getId()), role.name());
        }
    }

    @Test
    void splitNeedsAGuestCountThatLeavesAtLeastOneGuestAtEachTable() {
        var staff = ownWaiter();
        var a = session(table(TableStatus.AVAILABLE));
        var move = dish(a, "40000", 1);
        var free = table(TableStatus.AVAILABLE);
        for (int guests : new int[] {0, -1, 2, 3}) {
            assertThrows(
                    ApiException.class,
                    () ->
                            service.split(
                                    staff.getId(),
                                    a.getId(),
                                    free.getId(),
                                    whole(move),
                                    guests));
        }
        assertEquals(2, a.getGuestCount());
        assertEquals(TableStatus.AVAILABLE, free.getStatus());
    }

    @Test
    void splitRejectsBusyTableAndForeignOrCancelledItems() {
        var staff = ownWaiter();
        var a = session(table(TableStatus.AVAILABLE));
        var other = session(table(TableStatus.AVAILABLE));
        var mine = dish(a, "100000", 1);
        var foreign = dish(other, "1000", 1);
        var busy = other.getTable();
        var free = table(TableStatus.AVAILABLE);

        assertThrows(
                ApiException.class,
                () ->
                        service.split(
                                staff.getId(), a.getId(), busy.getId(), whole(mine), 1));
        assertThrows(
                ApiException.class,
                () ->
                        service.split(
                                staff.getId(),
                                a.getId(),
                                free.getId(),
                                whole(foreign),
                                1));
        mine.setStatus(OrderItem.Status.CANCELLED);
        assertThrows(
                ApiException.class,
                () ->
                        service.split(
                                staff.getId(), a.getId(), free.getId(), whole(mine), 1));
    }

    @Test
    void discountedBillsCannotBeMergedOrSplit() {
        var staff = user(Role.MANAGER);
        var a = session(table(TableStatus.AVAILABLE));
        var b = session(table(TableStatus.AVAILABLE));
        var item = dish(a, "100000", 1);
        a.getBill().setDiscountValue(new BigDecimal("5"));

        assertThrows(ApiException.class, () -> service.merge(staff.getId(), a.getId(), b.getId()));
        assertThrows(ApiException.class, () -> service.merge(staff.getId(), b.getId(), a.getId()));
        var free = table(TableStatus.AVAILABLE);
        assertThrows(
                ApiException.class,
                () ->
                        service.split(
                                staff.getId(), a.getId(), free.getId(), whole(item), 1));
    }

    @Test
    void billsWithCompletedOrPendingPaymentsCannotBeMergedOrSplit() {
        var staff = user(Role.MANAGER);
        for (var status : List.of(Payment.Status.COMPLETED, Payment.Status.PENDING)) {
            var a = session(table(TableStatus.AVAILABLE));
            var b = session(table(TableStatus.AVAILABLE));
            var item = dish(a, "100000", 1);
            var p = new Payment();
            p.setBill(a.getBill());
            p.setAmount(new BigDecimal("1000"));
            p.setMethod(Payment.Method.PAYOS);
            p.setStatus(status);
            db.save(p);
            var free = table(TableStatus.AVAILABLE);
            assertThrows(
                    ApiException.class, () -> service.merge(staff.getId(), a.getId(), b.getId()));
            assertThrows(
                    ApiException.class,
                    () ->
                            service.split(
                                    staff.getId(),
                                    a.getId(),
                                    free.getId(),
                                    whole(item),
                                    1));
        }
    }

    @Test
    void tablesWithAPaidDepositCannotBeMergedOrSplit() {
        var staff = user(Role.RECEPTIONIST);
        var a = session(table(TableStatus.AVAILABLE));
        var b = session(table(TableStatus.AVAILABLE));
        var item = dish(a, "100000", 1);
        var r = new Reservation();
        r.setCode("RS-" + UUID.randomUUID().toString().substring(0, 8));
        r.setCustomerName("Cọc");
        r.setPhone("0900000000");
        r.setArea(area);
        r.setGuestCount(2);
        r.setStartsAt(Instant.now());
        r.setEndsAt(Instant.now().plusSeconds(3600));
        r.setSource(Reservation.Source.WEB);
        db.save(r);
        a.setReservation(r);
        var deposit = new Payment();
        deposit.setReservation(r);
        deposit.setAmount(new BigDecimal("50000"));
        deposit.setMethod(Payment.Method.PAYOS);
        deposit.setStatus(Payment.Status.COMPLETED);
        deposit.setPaidAt(Instant.now());
        db.save(deposit);
        var free = table(TableStatus.AVAILABLE);

        assertThrows(ApiException.class, () -> service.merge(staff.getId(), a.getId(), b.getId()));
        assertThrows(
                ApiException.class,
                () ->
                        service.split(
                                staff.getId(), a.getId(), free.getId(), whole(item), 1));
    }

    @Test
    void sessionsAwaitingPaymentCannotChange() {
        var staff = user(Role.CASHIER);
        var a = session(table(TableStatus.AVAILABLE));
        var b = session(table(TableStatus.AVAILABLE));
        a.setStatus(TableSession.Status.PAYMENT_REQUESTED);
        assertThrows(ApiException.class, () -> service.merge(staff.getId(), a.getId(), b.getId()));
    }
}
