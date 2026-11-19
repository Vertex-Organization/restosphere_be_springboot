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
import com.vertex.restosphere.entity.MenuCategory;
import com.vertex.restosphere.entity.MenuItem;
import com.vertex.restosphere.entity.Order;
import com.vertex.restosphere.entity.OrderItem;
import com.vertex.restosphere.entity.RestaurantTable;
import com.vertex.restosphere.entity.TableSession;
import com.vertex.restosphere.entity.User;
import com.vertex.restosphere.repository.AuditLogRepository;
import com.vertex.restosphere.repository.RefreshTokenRepository;
import com.vertex.restosphere.repository.UserRepository;
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
    @Autowired UserRepository users;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired AuditLogRepository auditLogs;
    @Autowired EntityManager em;
    @Autowired TransactionTemplate tx;
    @Autowired PasswordEncoder passwordEncoder;

    User kitchen;
    User waiter;
    String kitchenToken;
    String waiterToken;
    // Du lieu tu tao, xoa nguoc thu tu tao de dung khoa ngoai.
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
        tx.executeWithoutResult(s -> {
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
    void noTokenIs401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void waiterIs403() throws Exception {
        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, waiterToken)).andExpect(status().isForbidden());
    }

    @Test
    void kitchenSeesItemsWithHistoryAndSkipsDraftOrders() throws Exception {
        Fixture f = fixture();
        Order o1 = persist(order(f.session, 1, Order.Status.SUBMITTED));
        Order o2 = persist(order(f.session, 2, Order.Status.SUBMITTED));
        Order draft = persist(order(f.session, 3, Order.Status.DRAFT));
        OrderItem preparing = persist(item(o1, f.menu, OrderItem.Status.PREPARING));
        preparing.setChef(kitchen);
        preparing.setStartedAt(Instant.now());
        preparing = persist(preparing);
        OrderItem submitted = persist(item(o2, f.menu, OrderItem.Status.SUBMITTED));
        persist(item(draft, f.menu, OrderItem.Status.SUBMITTED));
        persist(log(preparing, "start"));
        persist(log(preparing, "request-cancel")); // khong thuoc 3 action cua bep

        String body = list(null);
        List<Map<String, Object>> orders = JsonPath.read(body, "$");
        // Chi thay order cua session nay, theo thu tu cu truoc.
        List<String> ids = orders.stream().map(o -> (String) o.get("id")).toList();
        assertThat(ids).containsExactly(o1.getId().toString(), o2.getId().toString());

        Map<String, Object> first = orders.get(0);
        assertThat(first).containsEntry("tableCode", f.table.getCode()).containsEntry("area", f.area.getName())
                .containsEntry("sequence", 1).containsEntry("guests", 2)
                .containsEntry("waiterId", waiter.getId().toString());
        List<Map<String, Object>> items = JsonPath.read(body, "$[0].items");
        assertThat(items).hasSize(1);
        Map<String, Object> it = items.get(0);
        assertThat(it).containsEntry("id", preparing.getId().toString()).containsEntry("status", "PREPARING")
                .containsEntry("name", "Dish").containsEntry("startedBy", kitchen.getId().toString());
        List<Map<String, Object>> history = JsonPath.read(body, "$[0].items[0].history");
        assertThat(history).hasSize(1);
        assertThat(history.get(0)).containsEntry("status", "PREPARING").containsEntry("by",
                kitchen.getId().toString());
        assertThat((List<?>) JsonPath.read(body, "$[1].items[0].history")).isEmpty();
        assertThat((String) JsonPath.read(body, "$[1].items[0].id")).isEqualTo(submitted.getId().toString());
    }

    @Test
    void statusFilterKeepsOnlyMatchingItemsAndDropsEmptyOrders() throws Exception {
        Fixture f = fixture();
        Order o1 = persist(order(f.session, 1, Order.Status.SUBMITTED));
        Order o2 = persist(order(f.session, 2, Order.Status.SUBMITTED));
        persist(item(o1, f.menu, OrderItem.Status.PREPARING));
        OrderItem ready = persist(item(o2, f.menu, OrderItem.Status.READY));

        String body = list("READY");
        assertThat((List<?>) JsonPath.read(body, "$")).hasSize(1);
        assertThat((String) JsonPath.read(body, "$[0].id")).isEqualTo(o2.getId().toString());
        assertThat((List<?>) JsonPath.read(body, "$[0].items")).hasSize(1);
        assertThat((String) JsonPath.read(body, "$[0].items[0].id")).isEqualTo(ready.getId().toString());

        assertThat((List<?>) JsonPath.read(list("REJECTED"), "$")).isEmpty();
        mockMvc.perform(get(URL).param("status", "BOGUS").header(HttpHeaders.AUTHORIZATION, kitchenToken))
                .andExpect(status().isBadRequest());
    }

    private String list(String itemStatus) throws Exception {
        var req = get(URL).header(HttpHeaders.AUTHORIZATION, kitchenToken);
        if (itemStatus != null) {
            req = req.param("status", itemStatus);
        }
        return mockMvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
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
        TableSession session = new TableSession();
        session.setTable(table);
        session.setWaiter(waiter);
        session.setGuestCount(2);
        session.setStartedAt(Instant.now());
        session = persist(session);
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

    private Order order(TableSession session, int round, Order.Status status) {
        Order o = new Order();
        o.setSession(session);
        o.setWaiter(waiter);
        o.setRoundNumber(round);
        o.setStatus(status);
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
        // audit_logs duoc don trong cleanUsers, khong dua vao created.
        return auditLogs.save(a);
    }

    private <T> T persist(T entity) {
        if (entity instanceof AuditLog) {
            return entity;
        }
        T saved = tx.execute(s -> {
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
