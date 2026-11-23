package com.vertex.restosphere;

import static com.vertex.restosphere.common.util.Values.*;
import static org.junit.jupiter.api.Assertions.*;

import com.vertex.restosphere.common.enums.*;
import com.vertex.restosphere.common.util.Values;
import com.vertex.restosphere.dto.request.CashierOpenShiftRequest;
import com.vertex.restosphere.dto.request.CashierPayCashRequest;
import com.vertex.restosphere.dto.request.KitchenItemRef;
import com.vertex.restosphere.entity.*;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.mapper.NotificationMapper;
import com.vertex.restosphere.service.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class WorkspaceIntegrationTests {
    @Autowired WorkspaceStore db;
    @Autowired WorkspaceService workspace;
    @Autowired ReservationWorkflow reservations;
    @Autowired BillingWorkflow billing;
    @Autowired ServiceWorkflow service;
    @Autowired NotificationMapper notificationMapper;
    @Autowired KitchenService kitchen;
    @Autowired CashierShiftService cashierShifts;
    @Autowired CashierPaymentService payments;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;

    Employee user(Role role) {
        var u = new Employee();
        u.setEmail(UUID.randomUUID() + "@test.example");
        u.setFullName(role.name());
        u.setPasswordHash("unused");
        u.setRole(role);
        return db.save(u);
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> data(Map<String, Object> result) {
        return (Map<String, Object>) result.get("data");
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> list(Map<String, Object> state, String key) {
        return (List<Map<String, Object>>) state.get(key);
    }

    Map<String, Object> run(String role, Employee user, Object... args) {
        return workspace.command(id(user), map(args));
    }

    @Test
    void completeWorkflowAndPaymentReplay() {
        var reception = user(Role.RECEPTIONIST);
        var waiter = user(Role.WAITER);
        var cook = user(Role.KITCHEN);
        var cashier = user(Role.CASHIER);
        var area = new Area();
        area.setName("Workflow " + UUID.randomUUID());
        db.save(area);
        var table = new RestaurantTable();
        table.setArea(area);
        table.setCode("T-" + UUID.randomUUID().toString().substring(0, 8));
        table.setCapacity(4);
        table.setStatus(TableStatus.AVAILABLE);
        db.save(table);
        var cat = new MenuCategory();
        cat.setName("Test " + UUID.randomUUID());
        db.save(cat);
        var menu = new MenuItem();
        menu.setName("Dish");
        menu.setCategory(cat);
        menu.setPrice(new BigDecimal("100001"));
        db.save(menu);
        var input =
                map(
                        "customer",
                        map("name", "Guest", "phone", "0901234567"),
                        "startsAt",
                        Instant.now().plusSeconds(3600).toString(),
                        "guests",
                        2,
                        "duration",
                        120,
                        "areaId",
                        id(area),
                        "tableId",
                        id(table),
                        "notes",
                        "");
        var created = run("staff", reception, "type", "create", "input", input);
        String reservation = created.get("reservationId").toString();
        run("staff", reception, "type", "confirm", "id", reservation);
        run("staff", reception, "type", "check-in", "id", reservation, "actualGuests", 2);
        var state = data(run("staff", reception, "type", "handover", "id", reservation));
        String session =
                list(state, "tableSessions").stream()
                        .filter(s -> reservation.equals(s.get("reservationId")))
                        .findFirst()
                        .orElseThrow()
                        .get("id")
                        .toString();
        assertThrows(
                ApiException.class,
                () -> run("staff", reception, "type", "accept", "sessionId", session));
        run("staff", waiter, "type", "accept", "sessionId", session);
        var other = user(Role.WAITER);
        assertThrows(
                ApiException.class,
                () ->
                        run(
                                "staff",
                                other,
                                "type",
                                "add-draft",
                                "sessionId",
                                session,
                                "menuItemId",
                                id(menu)));
        run("staff", waiter, "type", "add-draft", "sessionId", session, "menuItemId", id(menu));
        state = data(run("staff", waiter, "type", "submit", "sessionId", session));
        var item = list(state, "orderItems").getFirst();
        String itemId = item.get("id").toString();
        assertThrows(
                ApiException.class,
                () -> run("staff", waiter, "type", "request-payment", "sessionId", session));
        var itemRef = new KitchenItemRef(itemId, ((Number) item.get("version")).longValue());
        kitchen.start(cook.getId(), List.of(itemRef));
        var current = db.get(OrderItem.class, itemId);
        assertThrows(
                ApiException.class,
                () -> kitchen.complete(cook.getId(), List.of(new KitchenItemRef(itemId, -1L))));
        kitchen.complete(cook.getId(), List.of(new KitchenItemRef(itemId, current.getVersion())));
        run("staff", waiter, "type", "serve", "sessionId", session, "itemId", itemId);
        state = data(run("staff", waiter, "type", "request-payment", "sessionId", session));
        var bill =
                list(state, "bills").stream()
                        .filter(b -> session.equals(b.get("sessionId")))
                        .findFirst()
                        .orElseThrow();
        cashierShifts.open(
                cashier.getId(), new CashierOpenShiftRequest(new BigDecimal("100000"), null));
        var billId = UUID.fromString(bill.get("id").toString());
        var payment =
                new CashierPayCashRequest(
                        ((Number) bill.get("version")).longValue(),
                        new BigDecimal("100001"),
                        new BigDecimal("110000"),
                        UUID.randomUUID().toString());
        var paid = payments.payCash(cashier.getId(), billId, payment);
        var replay = payments.payCash(cashier.getId(), billId, payment);
        assertTrue(paid.created());
        assertFalse(replay.created());
        assertEquals(paid.payment().id(), replay.payment().id());
        assertEquals(TableStatus.OCCUPIED, table.getStatus());
        assertEquals(1, db.all(Payment.class).size());
        var different =
                new CashierPayCashRequest(
                        payment.billVersion(),
                        new BigDecimal("100000"),
                        payment.tendered(),
                        payment.idempotencyKey());
        assertThrows(
                ApiException.class, () -> payments.payCash(cashier.getId(), billId, different));
        run("staff", waiter, "type", "guest-left", "sessionId", session, "confirmed", true);
        assertEquals(TableStatus.CLEANING, table.getStatus());
        run("staff", waiter, "type", "clean", "sessionId", session, "confirmed", true);
        assertEquals(TableStatus.AVAILABLE, table.getStatus());
    }

    @Test
    void currentDatabaseRoleAndEnabledStateOverrideTokenClaims() {
        var u = user(Role.KITCHEN);
        assertThrows(ApiException.class, () -> workspace.load("cashier", id(u)));
        u.setEnabled(false);
        assertThrows(ApiException.class, () -> workspace.load("kitchen", id(u)));
    }

    @Test
    void snapshotsKeepRoleSpecificRequestsAndNotificationTargets() {
        var cook = user(Role.KITCHEN);
        var cashier = user(Role.CASHIER);
        billing.request(
                ApprovalRequest.Type.MENU_STATUS,
                "MENU_ITEM",
                UUID.randomUUID(),
                BigDecimal.ZERO,
                "Unavailable",
                cook);
        billing.request(
                ApprovalRequest.Type.RESERVATION,
                "RESERVATION",
                UUID.randomUUID(),
                BigDecimal.ZERO,
                "Exception",
                user(Role.RECEPTIONIST));
        assertTrue(list(workspace.load("cashier", id(cashier)), "requests").isEmpty());
        String requestId = UUID.randomUUID().toString(), incidentId = UUID.randomUUID().toString();
        service.notify(Role.CASHIER, "REQUEST_DECIDED", "Decision", "Approved", requestId);
        service.notify(Role.MANAGER, "INCIDENT", "Incident", "Equipment", incidentId);
        service.notify(Role.KITCHEN, "INCIDENT_REPLY", "Reply", "Resolved", incidentId);
        var notification = list(workspace.load("cashier", id(cashier)), "notifications").getFirst();
        assertEquals(requestId, notification.get("requestId"));
        assertNull(notification.get("billId"));
        var managerNotification =
                db.all(Notification.class).stream()
                        .filter(x -> "INCIDENT".equals(x.getKind()))
                        .filter(x -> x.getAudienceRole() == Role.MANAGER)
                        .findFirst()
                        .orElseThrow();
        assertEquals(
                "/staff/manager/incidents?id=" + incidentId,
                notificationMapper.toMap(managerNotification, List.of()).get("path"));
        assertNull(
                list(workspace.load("kitchen", id(cook)), "notifications")
                        .getFirst()
                        .get("orderId"));
    }

    @Test
    void moneyAndQuantityRejectFractionalDongAndPortions() {
        assertThrows(ApiException.class, () -> Values.money(map("amount", 1.5), "amount"));
        var item = new MenuItem();
        assertThrows(
                ApiException.class, () -> Values.validateQuantity(item, new BigDecimal("1.5")));
        item.setPriceUnit(PriceUnit.KG);
        assertDoesNotThrow(() -> Values.validateQuantity(item, new BigDecimal("1.125")));
        assertEquals(
                new BigDecimal("125001"),
                Values.whole(new BigDecimal("100001").multiply(new BigDecimal("1.25"))));
    }

    @Test
    void reservationLookupRequiresPhoneAndOverlappingAssignmentsFail() {
        var staff = user(Role.RECEPTIONIST);
        var area = new Area();
        area.setName("Bookings " + UUID.randomUUID());
        db.save(area);
        var table = new RestaurantTable();
        table.setArea(area);
        table.setCode("B-" + UUID.randomUUID().toString().substring(0, 8));
        table.setCapacity(4);
        table.setStatus(TableStatus.AVAILABLE);
        db.save(table);
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
        assertEquals(
                code, reservations.lookup(map("code", code, "phone", "0901234567")).get("code"));
        assertThrows(
                ApiException.class,
                () -> reservations.lookup(map("code", code, "phone", "0907654321")));
        var r =
                db.all(Reservation.class).stream()
                        .filter(x -> x.getCode().equals(code))
                        .findFirst()
                        .orElseThrow();
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
        assertThrows(ApiException.class, () -> reservations.assign(second, id(table)));
        assertFalse(workspace.load("kitchen", id(user(Role.KITCHEN))).containsKey("reservations"));
    }

    @Test
    void httpBoundaryRequiresAuthenticationAndCurrentRole() throws Exception {
        var cook = user(Role.KITCHEN);
        db.flush();
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                "/api/v1/workspaces/kitchen"))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isUnauthorized());
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                        "/api/v1/workspaces/cashier")
                                .with(
                                        org.springframework.security.test.web.servlet.request
                                                .SecurityMockMvcRequestPostProcessors.jwt()
                                                .jwt(j -> j.subject(id(cook)))))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isForbidden());
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                "/api/v1/reservations/settings"))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isOk())
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                                        "$.areas")
                                .isArray());
    }
}
