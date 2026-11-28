package com.vertex.restosphere;

import static com.vertex.restosphere.common.util.Values.id;
import static com.vertex.restosphere.common.util.Values.map;
import static org.junit.jupiter.api.Assertions.*;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.common.enums.TableStatus;
import com.vertex.restosphere.entity.*;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.service.WorkspaceService;
import com.vertex.restosphere.service.WorkspaceStore;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class MergedTablePaymentRequestTests {
    @Autowired WorkspaceStore db;
    @Autowired WorkspaceService workspace;

    Employee reception;
    Employee waiter;
    TableSession first;
    TableSession second;

    Employee user(Role role) {
        var u = new Employee();
        u.setEmail(UUID.randomUUID() + "@test.example");
        u.setFullName(role.name());
        u.setPasswordHash("unused");
        u.setRole(role);
        return db.save(u);
    }

    RestaurantTable table(Area area) {
        var t = new RestaurantTable();
        t.setArea(area);
        t.setCode("P-" + UUID.randomUUID().toString().substring(0, 8));
        t.setCapacity(4);
        t.setStatus(TableStatus.AVAILABLE);
        return db.save(t);
    }

    MenuItem dish() {
        var category = new MenuCategory();
        category.setName("Cat " + UUID.randomUUID());
        db.save(category);
        var menu = new MenuItem();
        menu.setName("Dish");
        menu.setCategory(category);
        menu.setPrice(new BigDecimal("100000"));
        return db.save(menu);
    }

    void run(Employee who, Object... args) {
        workspace.command(id(who), map(args));
    }

    List<OrderItem> itemsOf(TableSession session) {
        return db.query(OrderItem.class, "from OrderItem i where i.order.session=?1", session);
    }

    void orderOneDish(TableSession session, MenuItem menu) {
        run(waiter, "type", "add-draft", "sessionId", id(session), "menuItemId", id(menu));
        run(waiter, "type", "submit", "sessionId", id(session));
    }

    void serveAll(TableSession session) {
        itemsOf(session).forEach(i -> i.setStatus(OrderItem.Status.SERVED));
    }

    @BeforeEach
    void seatTwoMergedTables() {
        reception = user(Role.RECEPTIONIST);
        waiter = user(Role.WAITER);
        var area = new Area();
        area.setName("Merged " + UUID.randomUUID());
        db.save(area);
        var input =
                map(
                        "customer",
                        map("name", "Group", "phone", "0907000009"),
                        "startsAt",
                        Instant.now().plusSeconds(3600).toString(),
                        "guests",
                        6,
                        "duration",
                        120,
                        "areaId",
                        id(area),
                        "tableIds",
                        List.of(id(table(area)), id(table(area))),
                        "notes",
                        "");
        String reservationId =
                workspace
                        .command(id(reception), map("type", "create", "input", input))
                        .get("reservationId")
                        .toString();
        run(reception, "type", "confirm", "id", reservationId);
        run(reception, "type", "check-in", "id", reservationId, "actualGuests", 6);
        run(reception, "type", "handover", "id", reservationId);
        var sessions =
                db.query(
                        TableSession.class,
                        "from TableSession s where s.reservation.id=?1 order by s.startedAt",
                        UUID.fromString(reservationId));
        first = sessions.get(0);
        second = sessions.get(1);
        run(waiter, "type", "accept", "sessionId", id(first));
        run(waiter, "type", "accept", "sessionId", id(second));
        var menu = dish();
        orderOneDish(first, menu);
        orderOneDish(second, menu);
    }

    @Test
    void paymentCannotBeRequestedWhileAnotherTableOfTheBillHasUnservedItems() {
        serveAll(first);

        var e =
                assertThrows(
                        ApiException.class,
                        () ->
                                run(
                                        waiter,
                                        "type",
                                        "request-payment",
                                        "sessionId",
                                        id(first)));

        assertEquals(409, e.getStatus().value());
        assertEquals(
                "Còn món chưa phục vụ ở bàn: "
                        + second.getTable().getCode()
                        + ". Cần phục vụ hết món của mọi bàn trong hóa đơn.",
                e.getMessage());
        assertEquals(TableSession.Status.OPEN, first.getStatus());
    }

    @Test
    void paymentCanBeRequestedOnceEveryTableOfTheBillIsServed() {
        serveAll(first);
        serveAll(second);

        run(waiter, "type", "request-payment", "sessionId", id(first));

        assertEquals(TableSession.Status.PAYMENT_REQUESTED, first.getStatus());
        assertNotNull(first.getBill().getRequestedAt());
    }
}
