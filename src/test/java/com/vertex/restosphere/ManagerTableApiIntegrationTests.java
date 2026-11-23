package com.vertex.restosphere;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.common.enums.TableStatus;
import com.vertex.restosphere.entity.Area;
import com.vertex.restosphere.entity.Employee;
import com.vertex.restosphere.entity.Reservation;
import com.vertex.restosphere.entity.ReservationTable;
import com.vertex.restosphere.entity.RestaurantTable;
import com.vertex.restosphere.entity.TableSession;
import com.vertex.restosphere.repository.AuditLogRepository;
import com.vertex.restosphere.repository.EmployeeRepository;
import com.vertex.restosphere.repository.RefreshTokenRepository;
import com.vertex.restosphere.repository.RestaurantTableRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ManagerTableApiIntegrationTests {

    private static final String MANAGER_EMAIL = "m3-manager@restosphere.test";
    private static final String PASSWORD = "correct-horse-battery";
    private static final String LIST_URL = "/api/v1/manager/tables";
    private static final String BUSY =
            "Table has an active session or reservation; use workspace commands";

    @Autowired MockMvc mockMvc;
    @Autowired EmployeeRepository users;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired AuditLogRepository auditLogs;
    @Autowired RestaurantTableRepository tables;
    @Autowired EntityManager em;
    @Autowired TransactionTemplate tx;
    @Autowired PasswordEncoder passwordEncoder;

    Employee manager;
    String token;
    Area areaA;
    Area areaB;
    // Du lieu tu tao (khu, ban, phien, lich dat), xoa nguoc thu tu tao de dung khoa ngoai.
    // Khong dung ban mau cua seed nen khong can khoi phuc.
    final List<Object> created = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        cleanUsers();
        manager = saveUser(MANAGER_EMAIL, Role.MANAGER);
        token = bearer(MANAGER_EMAIL);
        areaA = persist(area("M3 Khu A"));
        areaB = persist(area("M3 Khu B"));
    }

    @AfterEach
    void tearDown() {
        tx.executeWithoutResult(
                s -> {
                    for (int i = created.size() - 1; i >= 0; i--) {
                        em.remove(em.merge(created.get(i)));
                    }
                });
        created.clear();
        cleanUsers();
    }

    void cleanUsers() {
        // audit_logs va refresh_tokens tham chieu users nen xoa truoc.
        auditLogs.deleteAll();
        refreshTokens.deleteAll();
        users.deleteAll();
    }

    @Test
    void managerListShowsSessionAndActiveReservation() throws Exception {
        RestaurantTable served = persist(table("M3-A2", areaA, TableStatus.OCCUPIED));
        RestaurantTable booked = persist(table("M3-A1", areaA, TableStatus.AVAILABLE));
        RestaurantTable done = persist(table("M3-B1", areaB, TableStatus.AVAILABLE));
        TableSession session = openSession(served, TableSession.Status.OPEN);
        reserve(booked, Reservation.Status.CONFIRMED);
        reserve(done, Reservation.Status.COMPLETED);

        String body =
                mockMvc.perform(get(LIST_URL).header(HttpHeaders.AUTHORIZATION, token))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        List<Map<String, Object>> list = JsonPath.read(body, "$");
        assertThat(list).hasSize((int) tables.count());
        assertThat(list)
                .allSatisfy(
                        t ->
                                assertThat(t)
                                        .containsOnlyKeys(
                                                "id",
                                                "code",
                                                "areaId",
                                                "areaName",
                                                "seats",
                                                "status",
                                                "sessionId",
                                                "hasActiveReservation"));

        // Sap theo ten khu roi ma ban.
        List<String> codes = list.stream().map(t -> (String) t.get("code")).toList();
        assertThat(codes.indexOf("M3-A1")).isLessThan(codes.indexOf("M3-A2"));
        assertThat(codes.indexOf("M3-A2")).isLessThan(codes.indexOf("M3-B1"));

        Map<String, Object> s = byCode(list, "M3-A2");
        assertThat(s.get("sessionId")).isEqualTo(session.getId().toString());
        assertThat(s.get("areaName")).isEqualTo("M3 Khu A");
        assertThat(s.get("seats")).isEqualTo(4);
        assertThat(s.get("status")).isEqualTo("OCCUPIED");
        assertThat(s.get("hasActiveReservation")).isEqualTo(false);
        assertThat(byCode(list, "M3-A1").get("hasActiveReservation")).isEqualTo(true);
        assertThat(byCode(list, "M3-A1").get("sessionId")).isNull();
        assertThat(byCode(list, "M3-B1").get("hasActiveReservation")).isEqualTo(false);
    }

    @Test
    void updateLayoutAndOutOfServiceWithAudit() throws Exception {
        RestaurantTable t = persist(table("M3-T1", areaA, TableStatus.AVAILABLE));

        update(t, body("M3-T1", areaB, 6, true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacity").value(6))
                .andExpect(jsonPath("$.areaName").value("M3 Khu B"))
                .andExpect(jsonPath("$.status").value("OUT_OF_SERVICE"));
        assertThat(auditLogs.findAll())
                .singleElement()
                .satisfies(
                        a -> {
                            assertThat(a.getAction()).isEqualTo("TABLE_UPDATED");
                            assertThat(a.getTargetType()).isEqualTo("TABLE");
                            assertThat(a.getTargetId()).isEqualTo(t.getId());
                            assertThat(a.getActor().getId()).isEqualTo(manager.getId());
                            assertThat(a.getDescription())
                                    .isEqualTo(
                                            "Cập nhật bố trí bàn M3-T1 · 6 ghế · M3 Khu B · tạm"
                                                    + " ngưng");
                        });

        // Khong gui outOfService: giu OUT_OF_SERVICE.
        update(t, body("M3-T1", areaB, 8, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OUT_OF_SERVICE"));

        update(t, body("M3-T1", areaB, 8, false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
        assertThat(auditLogs.findAll())
                .anyMatch(
                        a ->
                                a.getDescription()
                                        .equals("Cập nhật bố trí bàn M3-T1 · 8 ghế · M3 Khu B"));
        long logs = auditLogs.count();

        // Gui lai y nguyen (co hoac khong co outOfService): khong ghi log moi.
        update(t, body("M3-T1", areaB, 8, false)).andExpect(status().isOk());
        update(t, body("M3-T1", areaB, 8, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
        assertThat(auditLogs.count()).isEqualTo(logs);
    }

    @Test
    void busyTablesAreConflictAndUnchanged() throws Exception {
        RestaurantTable open = persist(table("M3-O1", areaA, TableStatus.OCCUPIED));
        openSession(open, TableSession.Status.OPEN);
        RestaurantTable paying = persist(table("M3-P1", areaA, TableStatus.AVAILABLE));
        openSession(paying, TableSession.Status.PAYMENT_REQUESTED);
        RestaurantTable booked = persist(table("M3-R1", areaA, TableStatus.AVAILABLE));
        reserve(booked, Reservation.Status.CONFIRMED);
        RestaurantTable cleaning = persist(table("M3-C1", areaA, TableStatus.CLEANING));

        for (RestaurantTable t : List.of(open, paying, booked)) {
            update(t, body(t.getCode(), areaB, 6, true))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value(BUSY));
        }
        update(cleaning, body("M3-C1", areaB, 6, false))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Table is in service or being cleaned"));

        for (RestaurantTable t : List.of(open, paying, booked, cleaning)) {
            RestaurantTable now = tables.findById(t.getId()).orElseThrow();
            assertThat(now.getCapacity()).isEqualTo(4);
            assertThat(now.getStatus()).isEqualTo(t.getStatus());
        }
        assertThat(auditLogs.count()).isZero();
    }

    @Test
    void invalidInputAndNotFound() throws Exception {
        RestaurantTable t = persist(table("M3-V1", areaA, TableStatus.AVAILABLE));
        update(t, body("M3-V1", areaA, 0, null)).andExpect(status().isBadRequest());
        update(t, body("M3-V1", areaA, 101, null)).andExpect(status().isBadRequest());
        update(
                        t,
                        "{\"code\":\"M3-V1\",\"areaId\":\"%s\",\"capacity\":4}"
                                .formatted(UUID.randomUUID()))
                .andExpect(status().isNotFound());
        mockMvc.perform(
                        put("/api/v1/tables/" + UUID.randomUUID())
                                .header(HttpHeaders.AUTHORIZATION, token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("M3-V1", areaA, 4, null)))
                .andExpect(status().isNotFound());
        assertThat(tables.findById(t.getId()).orElseThrow().getCapacity()).isEqualTo(4);
        assertThat(auditLogs.count()).isZero();
    }

    @Test
    void accessControl() throws Exception {
        RestaurantTable t = persist(table("M3-X1", areaA, TableStatus.AVAILABLE));
        saveUser("m3-waiter@restosphere.test", Role.WAITER);
        String waiter = bearer("m3-waiter@restosphere.test");
        mockMvc.perform(get(LIST_URL).header(HttpHeaders.AUTHORIZATION, waiter))
                .andExpect(status().isForbidden());
        mockMvc.perform(
                        put("/api/v1/tables/" + t.getId())
                                .header(HttpHeaders.AUTHORIZATION, waiter)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("M3-X1", areaA, 6, null)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(LIST_URL)).andExpect(status().isUnauthorized());

        // Manager bi khoa trong DB nhung token con han.
        manager.setEnabled(false);
        users.save(manager);
        mockMvc.perform(get(LIST_URL).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
        update(t, body("M3-X1", areaA, 6, null)).andExpect(status().isForbidden());
        assertThat(tables.findById(t.getId()).orElseThrow().getCapacity()).isEqualTo(4);
    }

    private static Map<String, Object> byCode(List<Map<String, Object>> list, String code) {
        return list.stream().filter(t -> code.equals(t.get("code"))).findFirst().orElseThrow();
    }

    private <T> T persist(T entity) {
        tx.executeWithoutResult(s -> em.persist(entity));
        created.add(entity);
        return entity;
    }

    private static Area area(String name) {
        Area a = new Area();
        a.setName(name);
        return a;
    }

    private static RestaurantTable table(String code, Area area, TableStatus status) {
        RestaurantTable t = new RestaurantTable();
        t.setCode(code);
        t.setArea(area);
        t.setCapacity(4);
        t.setStatus(status);
        return t;
    }

    private TableSession openSession(RestaurantTable table, TableSession.Status status) {
        TableSession s = new TableSession();
        s.setTable(table);
        s.setGuestCount(2);
        s.setStartedAt(Instant.now());
        s.setStatus(status);
        return persist(s);
    }

    private void reserve(RestaurantTable table, Reservation.Status status) {
        Reservation r = new Reservation();
        r.setCode("M3-" + UUID.randomUUID().toString().substring(0, 8));
        r.setCustomerName("Khach M3");
        r.setPhone("0900000000");
        r.setArea(table.getArea());
        r.setGuestCount(2);
        r.setStartsAt(Instant.now().plus(1, ChronoUnit.HOURS));
        r.setEndsAt(Instant.now().plus(3, ChronoUnit.HOURS));
        r.setStatus(status);
        r.setSource(Reservation.Source.PHONE);
        persist(r);
        ReservationTable rt = new ReservationTable();
        rt.setReservation(r);
        rt.setTable(table);
        persist(rt);
    }

    private static String body(String code, Area area, int capacity, Boolean outOfService) {
        return "{\"code\":\"%s\",\"areaId\":\"%s\",\"capacity\":%d%s}"
                .formatted(
                        code,
                        area.getId(),
                        capacity,
                        outOfService == null ? "" : ",\"outOfService\":" + outOfService);
    }

    private ResultActions update(RestaurantTable t, String body) throws Exception {
        return mockMvc.perform(
                put("/api/v1/tables/" + t.getId())
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    private Employee saveUser(String email, Role role) {
        Employee u = new Employee();
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(PASSWORD));
        u.setFullName(role.name());
        u.setRole(role);
        return users.save(u);
    }

    private String bearer(String email) throws Exception {
        String body =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"email\":\"%s\",\"password\":\"%s\"}"
                                                        .formatted(email, PASSWORD)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.accessToken");
    }
}
