package com.vertex.restosphere;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.common.enums.TableStatus;
import com.vertex.restosphere.entity.Area;
import com.vertex.restosphere.entity.AuditLog;
import com.vertex.restosphere.entity.Bill;
import com.vertex.restosphere.entity.Employee;
import com.vertex.restosphere.entity.MenuCategory;
import com.vertex.restosphere.entity.MenuItem;
import com.vertex.restosphere.entity.Order;
import com.vertex.restosphere.entity.OrderItem;
import com.vertex.restosphere.entity.RestaurantSettings;
import com.vertex.restosphere.entity.RestaurantTable;
import com.vertex.restosphere.entity.TableSession;
import com.vertex.restosphere.repository.AuditLogRepository;
import com.vertex.restosphere.repository.EmployeeRepository;
import com.vertex.restosphere.repository.RefreshTokenRepository;
import com.vertex.restosphere.repository.RestaurantSettingsRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
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
class KitchenApiIntegrationTests {
    private static final String KITCHEN_EMAIL = "k1-kitchen@restosphere.test";
    private static final String WAITER_EMAIL = "k1-waiter@restosphere.test";
    private static final String PASSWORD = "correct-horse-battery";
    private static final String URL = "/api/v1/kitchen/orders";

    @Autowired MockMvc mockMvc;
    @Autowired EmployeeRepository users;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired AuditLogRepository auditLogs;
    @Autowired RestaurantSettingsRepository settings;
    @Autowired EntityManager em;
    @Autowired TransactionTemplate tx;
    @Autowired PasswordEncoder passwordEncoder;

    Employee kitchen;
    Employee waiter;
    String kitchenToken;
    String waiterToken;

    final List<Object> created = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        cleanUsers();
        kitchen = saveUser(KITCHEN_EMAIL, Role.KITCHEN);
        waiter = saveUser(WAITER_EMAIL, Role.WAITER);
        kitchenToken = bearer(KITCHEN_EMAIL);
        waiterToken = bearer(WAITER_EMAIL);
    }

    @AfterEach
    void tearDown() {
        tx.executeWithoutResult(
                s -> {
                    for (int i = created.size() - 1; i >= 0; i--) {
                        Object e = em.find(created.get(i).getClass(), idOf(created.get(i)));
                        if (e != null) {
                            em.remove(e);
                            em.flush();
                        }
                    }

                    em.createQuery(
                                    "delete from Notification where kind in"
                                            + " ('READY','ITEM_REJECTED')")
                            .executeUpdate();
                });
        created.clear();
        cleanUsers();
    }

    void cleanUsers() {
        auditLogs.deleteAll();
        refreshTokens.deleteAll();
        users.deleteAll();
    }

    @Test
    void noTokenIs401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void waiterIs403() throws Exception {
        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, waiterToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void kitchenSeesItemsWithHistory() throws Exception {
        Fixture f = fixture();
        Order o1 = persist(order(f.session, 1));
        Order o2 = persist(order(f.session, 2));
        OrderItem preparing = persist(item(o1, f.menu, OrderItem.Status.PREPARING));
        preparing.setChef(kitchen);
        preparing.setStartedAt(Instant.now());
        preparing = persist(preparing);
        OrderItem submitted = persist(item(o2, f.menu, OrderItem.Status.SUBMITTED));
        persist(log(preparing, "start"));
        persist(log(preparing, "request-cancel"));

        String body = list(null);
        List<Map<String, Object>> orders = JsonPath.read(body, "$");

        List<String> ids = orders.stream().map(o -> (String) o.get("id")).toList();
        assertThat(ids).containsExactly(o1.getId().toString(), o2.getId().toString());

        Map<String, Object> first = orders.get(0);
        assertThat(first)
                .containsEntry("tableCode", f.table.getCode())
                .containsEntry("area", f.area.getName())
                .containsEntry("sequence", 1)
                .containsEntry("guests", 2)
                .containsEntry("waiterId", waiter.getId().toString());
        List<Map<String, Object>> items = JsonPath.read(body, "$[0].items");
        assertThat(items).hasSize(1);
        Map<String, Object> it = items.get(0);
        assertThat(it)
                .containsEntry("id", preparing.getId().toString())
                .containsEntry("status", "PREPARING")
                .containsEntry("name", "Dish")
                .containsEntry("startedBy", kitchen.getId().toString());
        List<Map<String, Object>> history = JsonPath.read(body, "$[0].items[0].history");
        assertThat(history).hasSize(1);
        assertThat(history.get(0))
                .containsEntry("status", "PREPARING")
                .containsEntry("by", kitchen.getId().toString());
        assertThat((List<?>) JsonPath.read(body, "$[1].items[0].history")).isEmpty();
        assertThat((String) JsonPath.read(body, "$[1].items[0].id"))
                .isEqualTo(submitted.getId().toString());
    }

    @Test
    void statusFilterKeepsOnlyMatchingItemsAndDropsEmptyOrders() throws Exception {
        Fixture f = fixture();
        Order o1 = persist(order(f.session, 1));
        Order o2 = persist(order(f.session, 2));
        persist(item(o1, f.menu, OrderItem.Status.PREPARING));
        OrderItem ready = persist(item(o2, f.menu, OrderItem.Status.READY));

        String body = list("READY");
        assertThat((List<?>) JsonPath.read(body, "$")).hasSize(1);
        assertThat((String) JsonPath.read(body, "$[0].id")).isEqualTo(o2.getId().toString());
        assertThat((List<?>) JsonPath.read(body, "$[0].items")).hasSize(1);
        assertThat((String) JsonPath.read(body, "$[0].items[0].id"))
                .isEqualTo(ready.getId().toString());

        assertThat((List<?>) JsonPath.read(list("REJECTED"), "$")).isEmpty();
        mockMvc.perform(
                        get(URL).param("status", "BOGUS")
                                .header(HttpHeaders.AUTHORIZATION, kitchenToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void startThenCompleteChangesStatusAuditNotifyAndRevision() throws Exception {
        Fixture f = fixture();
        OrderItem item =
                persist(item(persist(order(f.session, 1)), f.menu, OrderItem.Status.SUBMITTED));
        long rev = revision();

        String started =
                kitchenPost(kitchenToken, "/items/start", items(item))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat((String) JsonPath.read(started, "$[0].status")).isEqualTo("PREPARING");
        assertThat((String) JsonPath.read(started, "$[0].startedBy"))
                .isEqualTo(kitchen.getId().toString());
        assertThat((Object) JsonPath.read(started, "$[0].startedAt")).isNotNull();
        long newVersion = ((Number) JsonPath.read(started, "$[0].version")).longValue();
        assertThat(newVersion).isGreaterThan(item.getVersion());
        assertThat((List<?>) JsonPath.read(started, "$[0].history")).hasSize(1);
        assertThat(revision()).isEqualTo(rev + 1);

        String done =
                kitchenPost(kitchenToken, "/items/complete", itemsJson(item.getId(), newVersion))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat((String) JsonPath.read(done, "$[0].status")).isEqualTo("READY");
        assertThat((Object) JsonPath.read(done, "$[0].readyAt")).isNotNull();
        assertThat((List<?>) JsonPath.read(done, "$[0].history")).hasSize(2);
        assertThat(revision()).isEqualTo(rev + 2);
        assertThat(
                        count(
                                "select count(n) from Notification n where n.kind = 'READY' and"
                                        + " n.path = '"
                                        + f.session.getId()
                                        + "'"))
                .isEqualTo(1);
        assertThat(count("select count(a) from AuditLog a where a.targetId = :p", item.getId()))
                .isEqualTo(2);
    }

    @Test
    void wrongVersionAndInvalidInputs() throws Exception {
        Fixture f = fixture();
        OrderItem item =
                persist(item(persist(order(f.session, 1)), f.menu, OrderItem.Status.SUBMITTED));
        String one = "{\"id\":\"" + item.getId() + "\",\"version\":0}";
        kitchenPost(kitchenToken, "/items/start", itemsJson(item.getId(), item.getVersion() + 5))
                .andExpect(status().isConflict());
        assertThat(reload(item).getStatus()).isEqualTo(OrderItem.Status.SUBMITTED);
        kitchenPost(kitchenToken, "/items/start", "{\"items\":[]}")
                .andExpect(status().isBadRequest());
        kitchenPost(kitchenToken, "/items/start", "{\"items\":[{\"id\":\"" + item.getId() + "\"}]}")
                .andExpect(status().isBadRequest());
        kitchenPost(kitchenToken, "/items/start", "{\"items\":[" + one + "," + one + "]}")
                .andExpect(status().isBadRequest());
        kitchenPost(kitchenToken, "/items/start", itemsJson("not-a-uuid", 0))
                .andExpect(status().isBadRequest());
        kitchenPost(kitchenToken, "/items/start", itemsJson(UUID.randomUUID(), 0))
                .andExpect(status().isNotFound());
        kitchenPost(
                        kitchenToken,
                        "/items/start",
                        "{\"items\":["
                                + String.join(",", java.util.Collections.nCopies(51, one))
                                + "]}")
                .andExpect(status().isBadRequest());
        assertThat(reload(item).getStatus()).isEqualTo(OrderItem.Status.SUBMITTED);
    }

    @Test
    void wrongStatusIs409() throws Exception {
        Fixture f = fixture();
        Order o = persist(order(f.session, 1));
        OrderItem submitted = persist(item(o, f.menu, OrderItem.Status.SUBMITTED));
        OrderItem preparing = persist(item(o, f.menu, OrderItem.Status.PREPARING));
        kitchenPost(kitchenToken, "/items/complete", items(submitted))
                .andExpect(status().isConflict());
        kitchenPost(kitchenToken, "/items/start", items(preparing))
                .andExpect(status().isConflict());
        kitchenPost(
                        kitchenToken,
                        "/items/" + preparing.getId() + "/reject",
                        rejectJson(preparing.getVersion(), "x"))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectSetsReasonRecalculatesBillAndReturnsRejectedAtBy() throws Exception {
        Fixture f = fixture();
        Order o = persist(order(f.session, 1));
        OrderItem item = persist(item(o, f.menu, OrderItem.Status.SUBMITTED));
        String res =
                kitchenPost(
                                kitchenToken,
                                "/items/" + item.getId() + "/reject",
                                rejectJson(item.getVersion(), "  Het hang  "))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat((String) JsonPath.read(res, "$[0].status")).isEqualTo("REJECTED");
        assertThat((String) JsonPath.read(res, "$[0].rejectionReason")).isEqualTo("Het hang");
        assertThat((Object) JsonPath.read(res, "$[0].rejectedAt")).isNotNull();
        assertThat((String) JsonPath.read(res, "$[0].rejectedBy"))
                .isEqualTo(kitchen.getId().toString());
        assertThat(count("select count(n) from Notification n where n.kind = 'ITEM_REJECTED'"))
                .isEqualTo(1);
        kitchenPost(kitchenToken, "/items/" + item.getId() + "/reject", rejectJson(0, " "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void oneBadItemRollsBackWholeBatch() throws Exception {
        Fixture f = fixture();
        Order o = persist(order(f.session, 1));
        OrderItem good = persist(item(o, f.menu, OrderItem.Status.SUBMITTED));
        OrderItem bad = persist(item(o, f.menu, OrderItem.Status.PREPARING));
        long rev = revision();
        String body =
                "{\"items\":[{\"id\":\""
                        + good.getId()
                        + "\",\"version\":"
                        + good.getVersion()
                        + "},{\"id\":\""
                        + bad.getId()
                        + "\",\"version\":"
                        + bad.getVersion()
                        + "}]}";
        kitchenPost(kitchenToken, "/items/start", body).andExpect(status().isConflict());
        assertThat(reload(good).getStatus()).isEqualTo(OrderItem.Status.SUBMITTED);
        assertThat(reload(good).getChef()).isNull();
        assertThat(count("select count(a) from AuditLog a where a.targetType = 'ORDER_ITEM'"))
                .isZero();
        assertThat(revision()).isEqualTo(rev);
    }

    @Test
    void waiterCannotChangeItems() throws Exception {
        Fixture f = fixture();
        OrderItem item =
                persist(item(persist(order(f.session, 1)), f.menu, OrderItem.Status.SUBMITTED));
        kitchenPost(waiterToken, "/items/start", items(item)).andExpect(status().isForbidden());
        kitchenPost(waiterToken, "/items/complete", items(item)).andExpect(status().isForbidden());
        kitchenPost(waiterToken, "/items/" + item.getId() + "/reject", rejectJson(0, "x"))
                .andExpect(status().isForbidden());
        assertThat(reload(item).getStatus()).isEqualTo(OrderItem.Status.SUBMITTED);
    }

    private ResultActions kitchenPost(String token, String path, String json) throws Exception {
        return mockMvc.perform(
                post("/api/v1/kitchen" + path)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json));
    }

    private static String itemsJson(Object id, long version) {
        return "{\"items\":[{\"id\":\"" + id + "\",\"version\":" + version + "}]}";
    }

    private String items(OrderItem item) {
        return itemsJson(item.getId(), reload(item).getVersion());
    }

    private static String rejectJson(long version, String reason) {
        return "{\"version\":" + version + ",\"reason\":\"" + reason + "\"}";
    }

    private OrderItem reload(OrderItem item) {
        return tx.execute(
                s -> {
                    OrderItem i = em.find(OrderItem.class, item.getId());
                    if (i.getChef() != null) {
                        i.getChef().getId();
                    }
                    return i;
                });
    }

    private long revision() {
        return settings.findBySingletonKey(1).map(RestaurantSettings::getRevision).orElse(0L);
    }

    private long count(String jpql, Object... params) {
        return tx.execute(
                s -> {
                    var q = em.createQuery(jpql, Long.class);
                    for (Object p : params) {
                        q.setParameter("p", p);
                    }
                    return q.getSingleResult();
                });
    }

    private String list(String itemStatus) throws Exception {
        var req = get(URL).header(HttpHeaders.AUTHORIZATION, kitchenToken);
        if (itemStatus != null) {
            req = req.param("status", itemStatus);
        }
        return mockMvc.perform(req)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private record Fixture(Area area, RestaurantTable table, TableSession session, MenuItem menu) {}

    private Fixture fixture() {
        Area area = new Area();
        area.setName("K1 Khu " + UUID.randomUUID());
        area = persist(area);
        RestaurantTable table = new RestaurantTable();
        table.setCode("K1-" + UUID.randomUUID().toString().substring(0, 8));
        table.setArea(area);
        table.setCapacity(4);
        table.setStatus(TableStatus.OCCUPIED);
        table = persist(table);
        Bill bill = new Bill();
        bill.setCode("K1-" + UUID.randomUUID().toString().substring(0, 8));
        bill = persist(bill);
        TableSession session = new TableSession();
        session.setBill(bill);
        session.setTable(table);
        session.setWaiter(waiter);
        session.setGuestCount(2);
        session.setStartedAt(Instant.now());
        session = persist(session);
        bill.getSessions().add(session);
        MenuCategory cat = new MenuCategory();
        cat.setName("K1 " + UUID.randomUUID());
        cat = persist(cat);
        MenuItem menu = new MenuItem();
        menu.setName("Dish");
        menu.setCategory(cat);
        menu.setPrice(new BigDecimal("100000"));
        menu = persist(menu);
        return new Fixture(area, table, session, menu);
    }

    private Order order(TableSession session, int round) {
        Order o = new Order();
        o.setSession(session);
        o.setWaiter(waiter);
        o.setRoundNumber(round);
        o.setStatus(Order.Status.SUBMITTED);
        o.setSubmittedAt(java.time.Instant.now());
        return o;
    }

    private static OrderItem item(Order order, MenuItem menu, OrderItem.Status status) {
        OrderItem i = new OrderItem();
        i.setOrder(order);
        i.setMenuItem(menu);
        i.setItemName(menu.getName());
        i.setUnit("dish");
        i.setQuantity(BigDecimal.ONE);
        i.setUnitPrice(menu.getPrice());
        i.setStatus(status);
        return i;
    }

    private AuditLog log(OrderItem item, String action) {
        AuditLog a = new AuditLog();
        a.setActor(kitchen);
        a.setAction(action);
        a.setTargetType("ORDER_ITEM");
        a.setTargetId(item.getId());
        a.setDescription(action);

        return auditLogs.save(a);
    }

    private <T> T persist(T entity) {
        if (entity instanceof AuditLog) {
            return entity;
        }
        T saved =
                tx.execute(
                        s -> {
                            T merged = em.merge(entity);
                            em.flush();
                            return merged;
                        });
        if (!created.contains(saved)) {
            created.removeIf(c -> c.getClass() == saved.getClass() && idOf(c).equals(idOf(saved)));
            created.add(saved);
        }
        return saved;
    }

    private static Object idOf(Object e) {
        try {
            return e.getClass().getMethod("getId").invoke(e);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
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
