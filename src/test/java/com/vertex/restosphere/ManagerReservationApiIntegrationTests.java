package com.vertex.restosphere;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.common.enums.TableStatus;
import com.vertex.restosphere.entity.Area;
import com.vertex.restosphere.entity.MenuCategory;
import com.vertex.restosphere.entity.MenuItem;
import com.vertex.restosphere.entity.Reservation;
import com.vertex.restosphere.entity.ReservationHistory;
import com.vertex.restosphere.entity.ReservationItem;
import com.vertex.restosphere.entity.ReservationTable;
import com.vertex.restosphere.entity.RestaurantSettings;
import com.vertex.restosphere.entity.RestaurantTable;
import com.vertex.restosphere.entity.TableSession;
import com.vertex.restosphere.entity.User;
import com.vertex.restosphere.repository.AuditLogRepository;
import com.vertex.restosphere.repository.RefreshTokenRepository;
import com.vertex.restosphere.repository.ReservationRepository;
import com.vertex.restosphere.repository.RestaurantSettingsRepository;
import com.vertex.restosphere.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ManagerReservationApiIntegrationTests {

    private static final String MANAGER_EMAIL = "m4-manager@restosphere.test";
    private static final String PASSWORD = "correct-horse-battery";
    private static final String URL = "/api/v1/manager/reservations";
    private static final String REASON = "Khách xin ngồi gần cửa sổ";

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository users;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired AuditLogRepository auditLogs;
    @Autowired ReservationRepository reservations;
    @Autowired RestaurantSettingsRepository settings;
    @Autowired EntityManager em;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;

    User manager;
    String token;
    Area areaA;
    Area areaB;
    // Id du lieu tu tao; xoa bang SQL theo thu tu khoa ngoai (PATCH tao them dong gan ban/lich su).
    final List<UUID> reservationIds = new ArrayList<>();
    final List<UUID> sessionIds = new ArrayList<>();
    final List<UUID> tableIds = new ArrayList<>();
    final List<UUID> areaIds = new ArrayList<>();
    final List<UUID> menuItemIds = new ArrayList<>();
    final List<UUID> categoryIds = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        cleanUsers();
        manager = saveUser(MANAGER_EMAIL, Role.MANAGER);
        token = bearer(MANAGER_EMAIL);
        areaA = area("M4 Khu A");
        areaB = area("M4 Khu B");
    }

    @AfterEach
    void tearDown() {
        for (UUID id : reservationIds) {
            jdbc.update("delete from reservation_histories where reservation_id = ?", id);
            jdbc.update("delete from reservation_tables where reservation_id = ?", id);
            jdbc.update("delete from reservation_items where reservation_id = ?", id);
        }
        sessionIds.forEach(id -> jdbc.update("delete from table_sessions where id = ?", id));
        reservationIds.forEach(id -> jdbc.update("delete from reservations where id = ?", id));
        tableIds.forEach(id -> jdbc.update("delete from tables where id = ?", id));
        areaIds.forEach(id -> jdbc.update("delete from areas where id = ?", id));
        menuItemIds.forEach(id -> jdbc.update("delete from menu_items where id = ?", id));
        categoryIds.forEach(id -> jdbc.update("delete from menu_categories where id = ?", id));
        List.of(reservationIds, sessionIds, tableIds, areaIds, menuItemIds, categoryIds).forEach(List::clear);
        cleanUsers();
    }

    void cleanUsers() {
        // audit_logs va refresh_tokens tham chieu users nen xoa truoc.
        auditLogs.deleteAll();
        refreshTokens.deleteAll();
        users.deleteAll();
    }

    @Test
    void listHasFieldsTableItemsAndEmployee() throws Exception {
        RestaurantTable t = table("M4-L1", areaA, 4, TableStatus.AVAILABLE);
        Reservation r = reservation(areaA, future(1, 19), 4, Reservation.Status.CONFIRMED);
        assignTable(r, t);
        MenuItem dish = dish("M4 Cua hấp");
        tx.executeWithoutResult(s -> {
            ReservationItem i = new ReservationItem();
            i.setReservation(em.find(Reservation.class, r.getId()));
            i.setMenuItem(em.find(MenuItem.class, dish.getId()));
            i.setQuantity(new BigDecimal("1.5"));
            i.setUnitPrice(new BigDecimal("890000"));
            em.persist(i);
        });
        User waiter = saveUser("m4-waiter@restosphere.test", Role.WAITER);
        // Cu -> moi: waiter, manager, khong actor. employeeId = manager (dong moi nhat CO actor).
        history(r, waiter, "2026-01-01T01:00:00Z");
        history(r, manager, "2026-01-01T02:00:00Z");
        history(r, null, "2026-01-01T03:00:00Z");
        Reservation bare = reservation(areaA, future(1, 20), 2, Reservation.Status.PENDING);

        String body = mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Map<String, Object> json = byId(body, r.getId());
        assertThat(json).containsOnlyKeys("id", "code", "customer", "phone", "email", "occasion", "areaId", "areaName",
                "source", "guests", "actualGuests", "at", "until", "tableId", "tableCode", "status", "employeeId",
                "note", "preorder");
        assertThat(json.get("tableId")).isEqualTo(t.getId().toString());
        assertThat(json.get("tableCode")).isEqualTo("M4-L1");
        assertThat(json.get("areaName")).isEqualTo("M4 Khu A");
        assertThat(json.get("email")).isEqualTo("");
        assertThat(json.get("note")).isEqualTo("");
        assertThat(json.get("actualGuests")).isNull();
        assertThat(json.get("employeeId")).isEqualTo(manager.getId().toString());
        assertThat(JsonPath.<List<String>>read(body, "$[?(@.id == '%s')].preorder[*].menuItemName".formatted(r.getId())))
                .containsExactly("M4 Cua hấp");
        assertThat(JsonPath.<List<Double>>read(body, "$[?(@.id == '%s')].preorder[*].quantity".formatted(r.getId())))
                .containsExactly(1.5);

        Map<String, Object> other = byId(body, bare.getId());
        assertThat(other.get("employeeId")).isEqualTo("");
        assertThat(other.get("tableId")).isNull();
        assertThat(other.get("tableCode")).isNull();

        // Sap xep theo startsAt tang dan.
        List<String> at = JsonPath.read(body, "$[*].at");
        assertThat(at).isSortedAccordingTo((x, y) -> Instant.parse(x).compareTo(Instant.parse(y)));
    }

    @Test
    void dateFilterUsesRestaurantTimezone() throws Exception {
        ZoneId zone = ZoneId.of(settings.findBySingletonKey(1).map(RestaurantSettings::getTimezone)
                .orElse("Asia/Ho_Chi_Minh"));
        LocalDate day = LocalDate.of(2027, 3, 10);
        Reservation early = reservation(areaA, day.atTime(0, 30).atZone(zone).toInstant(), 2,
                Reservation.Status.CONFIRMED);
        Reservation lastNight = reservation(areaA, day.minusDays(1).atTime(LocalTime.of(23, 59)).atZone(zone)
                .toInstant(), 2, Reservation.Status.CONFIRMED);

        String body = mockMvc.perform(get(URL + "?from=2027-03-10&to=2027-03-10").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(body, "$[*].id");
        assertThat(ids).contains(early.getId().toString()).doesNotContain(lastNight.getId().toString());

        String prev = mockMvc.perform(get(URL + "?to=2027-03-09").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(prev, "$[*].id"))
                .contains(lastNight.getId().toString()).doesNotContain(early.getId().toString());
    }

    @Test
    void statusFilterAndBadParams() throws Exception {
        Reservation confirmed = reservation(areaA, future(2, 19), 2, Reservation.Status.CONFIRMED);
        Reservation pending = reservation(areaA, future(2, 20), 2, Reservation.Status.PENDING);
        String body = mockMvc.perform(get(URL + "?status=CONFIRMED").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(body, "$[*].status")).containsOnly("CONFIRMED");
        assertThat(JsonPath.<List<String>>read(body, "$[*].id"))
                .contains(confirmed.getId().toString()).doesNotContain(pending.getId().toString());

        for (String query : List.of("?status=ABC", "?from=2026-11-20&to=2026-11-19", "?from=19-11-2026")) {
            mockMvc.perform(get(URL + query).header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void changeConfirmedTableWritesHistoryAndAudit() throws Exception {
        RestaurantTable from = table("M4-C1", areaA, 4, TableStatus.AVAILABLE);
        RestaurantTable to = table("M4-C2", areaA, 6, TableStatus.AVAILABLE);
        Reservation r = reservation(areaA, future(3, 19), 4, Reservation.Status.CONFIRMED);
        assignTable(r, from);

        change(r.getId(), to.getId(), "  " + REASON + "  ").andExpect(status().isOk())
                .andExpect(jsonPath("$.tableId").value(to.getId().toString()))
                .andExpect(jsonPath("$.tableCode").value("M4-C2"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        List<Map<String, Object>> rows = jdbc.queryForList(
                "select action, description, actor_id from reservation_histories where reservation_id = ?", r.getId());
        assertThat(rows).singleElement().satisfies(h -> {
            assertThat(h.get("action")).isEqualTo("reservation.move");
            assertThat(h.get("description")).isEqualTo(REASON);
            assertThat(h.get("actor_id")).isEqualTo(manager.getId());
        });
        assertThat(jdbc.queryForList("select table_id from reservation_tables where reservation_id = ?", UUID.class,
                r.getId())).containsExactly(to.getId());
        assertThat(auditLogs.findAll()).singleElement().satisfies(a -> {
            assertThat(a.getAction()).isEqualTo("RESERVATION_TABLE_CHANGED");
            assertThat(a.getTargetType()).isEqualTo("RESERVATION");
            assertThat(a.getTargetId()).isEqualTo(r.getId());
            assertThat(a.getActor().getId()).isEqualTo(manager.getId());
            assertThat(a.getDescription()).isEqualTo("Điều chỉnh " + r.getCode() + ": M4-C1 → M4-C2. " + REASON);
        });
    }

    @Test
    void changePendingWithoutTable() throws Exception {
        RestaurantTable to = table("M4-P1", areaA, 4, TableStatus.AVAILABLE);
        Reservation r = reservation(areaA, future(4, 19), 2, Reservation.Status.PENDING);
        change(r.getId(), to.getId(), REASON).andExpect(status().isOk())
                .andExpect(jsonPath("$.tableId").value(to.getId().toString()));
        assertThat(auditLogs.findAll()).singleElement().satisfies(a -> assertThat(a.getDescription())
                .isEqualTo("Điều chỉnh " + r.getCode() + ": chưa có bàn → M4-P1. " + REASON));
    }

    @Test
    void badRequestsAndNotFound() throws Exception {
        RestaurantTable t = table("M4-B1", areaA, 4, TableStatus.AVAILABLE);
        Reservation r = reservation(areaA, future(5, 19), 2, Reservation.Status.CONFIRMED);
        assignTable(r, t);
        change(r.getId(), t.getId(), "abc").andExpect(status().isBadRequest());
        change(r.getId(), t.getId(), "        ").andExpect(status().isBadRequest());
        change(r.getId(), t.getId(), REASON).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Choose a different table"));
        mockMvc.perform(patch(URL + "/" + r.getId() + "/table").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"" + REASON + "\"}"))
                .andExpect(status().isBadRequest());
        change(UUID.randomUUID(), t.getId(), REASON).andExpect(status().isNotFound());
        change(r.getId(), UUID.randomUUID(), REASON).andExpect(status().isNotFound());
        assertThat(auditLogs.count()).isZero();
    }

    @Test
    void conflictsLeaveReservationUnchanged() throws Exception {
        RestaurantTable current = table("M4-X0", areaA, 4, TableStatus.AVAILABLE);
        Instant start = future(6, 19);
        Reservation r = reservation(areaA, start, 4, Reservation.Status.CONFIRMED);
        assignTable(r, current);

        RestaurantTable otherArea = table("M4-X1", areaB, 6, TableStatus.AVAILABLE);
        RestaurantTable small = table("M4-X2", areaA, 2, TableStatus.AVAILABLE);
        RestaurantTable broken = table("M4-X3", areaA, 6, TableStatus.OUT_OF_SERVICE);
        RestaurantTable busy = table("M4-X4", areaA, 6, TableStatus.AVAILABLE);
        // Lich khac bat dau sau khi r ket thuc, nhung van nam trong thoi gian dem -> van trung.
        int buffer = settings.findBySingletonKey(1).map(RestaurantSettings::getBufferMinutes).orElse(15);
        assertThat(buffer).as("can thoi gian dem > 0 de thu").isPositive();
        Reservation later = reservation(areaA, start.plus(2, ChronoUnit.HOURS).plusSeconds(buffer * 30L), 2,
                Reservation.Status.CONFIRMED);
        assignTable(later, busy);

        for (RestaurantTable t : List.of(otherArea, small, broken, busy)) {
            change(r.getId(), t.getId(), REASON).andExpect(status().isConflict());
        }
        for (Reservation.Status s : List.of(Reservation.Status.SEATED, Reservation.Status.COMPLETED,
                Reservation.Status.CANCELLED)) {
            Reservation closed = reservation(areaA, future(7, 19), 2, s);
            change(closed.getId(), small.getId(), REASON).andExpect(status().isConflict());
        }

        // Khach da den: ban moi phai trong ngay.
        Reservation arrived = reservation(areaA, future(8, 19), 2, Reservation.Status.ARRIVED);
        assignTable(arrived, current);
        RestaurantTable occupied = table("M4-X5", areaA, 4, TableStatus.OCCUPIED);
        RestaurantTable withSession = table("M4-X6", areaA, 4, TableStatus.AVAILABLE);
        session(withSession);
        for (RestaurantTable t : List.of(occupied, withSession)) {
            change(arrived.getId(), t.getId(), REASON).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value("Table is not available now"));
        }

        assertThat(jdbc.queryForList("select table_id from reservation_tables where reservation_id = ?", UUID.class,
                r.getId())).containsExactly(current.getId());
        assertThat(auditLogs.count()).isZero();

        RestaurantTable free = table("M4-X7", areaA, 4, TableStatus.AVAILABLE);
        change(arrived.getId(), free.getId(), REASON).andExpect(status().isOk())
                .andExpect(jsonPath("$.tableId").value(free.getId().toString()))
                .andExpect(jsonPath("$.status").value("ARRIVED"));
    }

    @Test
    void accessControl() throws Exception {
        RestaurantTable t = table("M4-A1", areaA, 4, TableStatus.AVAILABLE);
        Reservation r = reservation(areaA, future(9, 19), 2, Reservation.Status.PENDING);
        saveUser("m4-waiter@restosphere.test", Role.WAITER);
        String waiter = bearer("m4-waiter@restosphere.test");
        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, waiter)).andExpect(status().isForbidden());
        mockMvc.perform(patch(URL + "/" + r.getId() + "/table").header(HttpHeaders.AUTHORIZATION, waiter)
                        .contentType(MediaType.APPLICATION_JSON).content(body(t.getId(), REASON)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());

        // Manager bi khoa trong DB nhung token con han.
        manager.setEnabled(false);
        users.save(manager);
        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isForbidden());
        change(r.getId(), t.getId(), REASON).andExpect(status().isForbidden());
        assertThat(jdbc.queryForList("select table_id from reservation_tables where reservation_id = ?", UUID.class,
                r.getId())).isEmpty();
    }

    @Test
    void oldWorkspacesSeeTheMove() throws Exception {
        RestaurantTable from = table("M4-W1", areaA, 4, TableStatus.AVAILABLE);
        RestaurantTable to = table("M4-W2", areaA, 4, TableStatus.AVAILABLE);
        Reservation r = reservation(areaA, future(10, 19), 2, Reservation.Status.CONFIRMED);
        assignTable(r, from);
        change(r.getId(), to.getId(), REASON).andExpect(status().isOk());

        String id = r.getId().toString();
        mockMvc.perform(get("/api/v1/workspaces/manager").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservations[?(@.id == '%s')].tableId".formatted(id)).value(to.getId().toString()))
                .andExpect(jsonPath("$.history[?(@.entityId == '%s')].text".formatted(id))
                        .value("Điều chỉnh " + r.getCode() + ": M4-W1 → M4-W2. " + REASON));

        saveUser("m4-reception@restosphere.test", Role.RECEPTIONIST);
        mockMvc.perform(get("/api/v1/workspaces/staff")
                        .header(HttpHeaders.AUTHORIZATION, bearer("m4-reception@restosphere.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservations[?(@.id == '%s')].history[*].text".formatted(id)).value(hasItem(REASON)));
    }

    // ---- du lieu ----

    private static Instant future(int days, int hourUtc) {
        return Instant.now().truncatedTo(ChronoUnit.DAYS).plus(days + 30L, ChronoUnit.DAYS).plus(hourUtc, ChronoUnit.HOURS);
    }

    private Map<String, Object> byId(String body, UUID id) {
        List<Map<String, Object>> found = JsonPath.read(body, "$[?(@.id == '%s')]".formatted(id));
        assertThat(found).hasSize(1);
        return found.getFirst();
    }

    private <T> T persist(T entity) {
        tx.executeWithoutResult(s -> em.persist(entity));
        return entity;
    }

    private Area area(String name) {
        Area a = new Area();
        a.setName(name);
        persist(a);
        areaIds.add(a.getId());
        return a;
    }

    private RestaurantTable table(String code, Area area, int capacity, TableStatus status) {
        RestaurantTable t = new RestaurantTable();
        t.setCode(code);
        t.setArea(area);
        t.setCapacity(capacity);
        t.setStatus(status);
        persist(t);
        tableIds.add(t.getId());
        return t;
    }

    private Reservation reservation(Area area, Instant start, int guests, Reservation.Status status) {
        Reservation r = new Reservation();
        r.setCode("RS-M4" + UUID.randomUUID().toString().replace("-", "").substring(0, 11).toUpperCase());
        r.setCustomerName("Khách M4");
        r.setPhone("0901234567");
        r.setArea(area);
        r.setGuestCount(guests);
        r.setStartsAt(start);
        r.setEndsAt(start.plus(2, ChronoUnit.HOURS));
        r.setStatus(status);
        r.setSource(Reservation.Source.PHONE);
        persist(r);
        reservationIds.add(r.getId());
        return r;
    }

    private void assignTable(Reservation r, RestaurantTable t) {
        ReservationTable rt = new ReservationTable();
        rt.setReservation(r);
        rt.setTable(t);
        persist(rt);
    }

    private void session(RestaurantTable t) {
        TableSession s = new TableSession();
        s.setTable(t);
        s.setGuestCount(2);
        s.setStartedAt(Instant.now());
        persist(s);
        sessionIds.add(s.getId());
    }

    private MenuItem dish(String name) {
        MenuCategory c = new MenuCategory();
        c.setName("M4 Danh muc");
        c.setSortOrder(900);
        persist(c);
        categoryIds.add(c.getId());
        MenuItem m = new MenuItem();
        m.setName(name);
        m.setCategory(c);
        m.setPrice(new BigDecimal("890000"));
        persist(m);
        menuItemIds.add(m.getId());
        return m;
    }

    private void history(Reservation r, User actor, String at) {
        ReservationHistory h = new ReservationHistory();
        h.setReservation(r);
        h.setActor(actor);
        h.setAction("test");
        h.setDescription("test");
        persist(h);
        // Dat created_at co dinh de thu tu "moi nhat" khong phu thuoc toc do may.
        jdbc.update("update reservation_histories set created_at = ? where id = ?", Timestamp.from(Instant.parse(at)),
                h.getId());
    }

    private static String body(UUID tableId, String reason) {
        return "{\"tableId\":\"%s\",\"reason\":\"%s\"}".formatted(tableId, reason);
    }

    private ResultActions change(UUID id, UUID tableId, String reason) throws Exception {
        return mockMvc.perform(patch(URL + "/" + id + "/table").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(body(tableId, reason)));
    }

    private User saveUser(String email, Role role) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(PASSWORD));
        u.setFullName(role.name());
        u.setRole(role);
        return users.save(u);
    }

    private String bearer(String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.accessToken");
    }
}
