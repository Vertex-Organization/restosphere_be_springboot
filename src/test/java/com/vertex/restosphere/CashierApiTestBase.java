package com.vertex.restosphere;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.common.enums.TableStatus;
import com.vertex.restosphere.entity.Area;
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
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
abstract class CashierApiTestBase {
    static final String PASSWORD = "correct-horse-battery";
    static final String BASE = "/api/v1/cashier";

    @Autowired MockMvc mockMvc;
    @Autowired EmployeeRepository users;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired AuditLogRepository auditLogs;
    @Autowired RestaurantSettingsRepository settings;
    @Autowired EntityManager em;
    @Autowired TransactionTemplate tx;
    @Autowired PasswordEncoder passwordEncoder;

    Employee cashier;
    Employee waiter;
    String cashierToken;
    String waiterToken;
    final List<Employee> testUsers = new ArrayList<>();

    final List<Object> created = new ArrayList<>();

    abstract String prefix();

    @BeforeEach
    void setUp() throws Exception {
        cleanUsers();
        cashier = saveUser(prefix() + "-cashier@restosphere.test", Role.CASHIER);
        waiter = saveUser(prefix() + "-waiter@restosphere.test", Role.WAITER);
        cashierToken = bearer(cashier.getEmail());
        waiterToken = bearer(waiter.getEmail());
    }

    @AfterEach
    void tearDown() {
        tx.executeWithoutResult(
                s -> {
                    if (!testUsers.isEmpty()) {
                        em.createQuery("delete from NotificationRead r where r.employee in :u")
                                .setParameter("u", testUsers)
                                .executeUpdate();
                        em.createQuery("delete from Payment p where p.cashierShift.cashier in :u")
                                .setParameter("u", testUsers)
                                .executeUpdate();
                        em.createQuery("delete from ApprovalRequest a where a.requestedBy in :u")
                                .setParameter("u", testUsers)
                                .executeUpdate();
                        em.createQuery("delete from CashierShift c where c.cashier in :u")
                                .setParameter("u", testUsers)
                                .executeUpdate();
                    }
                    em.createQuery("delete from Notification where kind = 'PAYMENT'")
                            .executeUpdate();
                    for (int i = created.size() - 1; i >= 0; i--) {
                        Object e = em.find(created.get(i).getClass(), idOf(created.get(i)));
                        if (e != null) {
                            em.remove(e);
                            em.flush();
                        }
                    }
                });
        created.clear();
        cleanUsers();
    }

    void cleanUsers() {
        auditLogs.deleteAll();
        refreshTokens.deleteAll();
        users.deleteAll();
        testUsers.clear();
    }

    ResultActions call(HttpMethod method, String path, String token, String json) throws Exception {
        var req = request(method, BASE + path);
        if (token != null) {
            req = req.header(HttpHeaders.AUTHORIZATION, token);
        }
        if (json != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(req);
    }

    String ok(ResultActions r) throws Exception {
        return r.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    String created(ResultActions r) throws Exception {
        return r.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    <T> T json(String body, String path) {
        return JsonPath.read(body, path);
    }

    long revision() {
        return settings.findBySingletonKey(1).map(RestaurantSettings::getRevision).orElse(0L);
    }

    long count(String jpql, Object... params) {
        return tx.execute(
                s -> {
                    var q = em.createQuery(jpql, Long.class);
                    for (int i = 0; i < params.length; i++) {
                        q.setParameter("p" + i, params[i]);
                    }
                    return q.getSingleResult();
                });
    }

    long auditCount(String action) {
        return count(
                "select count(a) from AuditLog a where a.targetType = 'WORKSPACE' and a.action ="
                        + " :p0",
                action);
    }

    String openShift(String token, long openingFloat) throws Exception {
        String body =
                created(
                        call(
                                HttpMethod.POST,
                                "/shifts/open",
                                token,
                                "{\"openingFloat\":%d}".formatted(openingFloat)));
        return json(body, "$.id");
    }

    record Fixture(
            Area area,
            RestaurantTable table,
            TableSession session,
            MenuItem menu,
            Order order,
            OrderItem item,
            Bill bill) {}

    Fixture billFixture() {
        Area area = new Area();
        area.setName(prefix() + " Area " + UUID.randomUUID());
        area = persist(area);
        RestaurantTable table = new RestaurantTable();
        table.setCode(prefix() + "-" + UUID.randomUUID().toString().substring(0, 8));
        table.setArea(area);
        table.setCapacity(4);
        table.setStatus(TableStatus.OCCUPIED);
        table = persist(table);
        TableSession session = new TableSession();
        session.setTable(table);
        session.setWaiter(waiter);
        session.setGuestCount(2);
        session.setStartedAt(Instant.now());
        session.setStatus(TableSession.Status.PAYMENT_REQUESTED);
        session = persist(session);
        MenuItem menu = menu("Dish");
        Order order = order(session, 1);
        OrderItem item = item(order, menu, OrderItem.Status.SERVED);
        Bill bill = new Bill();
        bill.setCode(prefix() + "-B-" + UUID.randomUUID().toString().substring(0, 8));
        bill.setSession(session);
        bill.setSubtotal(new BigDecimal("100000"));
        bill.setTotal(new BigDecimal("100000"));
        bill.setRequestedAt(Instant.now());
        bill = persist(bill);
        return new Fixture(area, table, session, menu, order, item, bill);
    }

    MenuItem menu(String name) {
        MenuCategory cat = new MenuCategory();
        cat.setName(prefix() + " " + UUID.randomUUID());
        cat = persist(cat);
        MenuItem menu = new MenuItem();
        menu.setName(name);
        menu.setCategory(cat);
        menu.setPrice(new BigDecimal("100000"));
        return persist(menu);
    }

    Order order(TableSession session, int round) {
        Order o = new Order();
        o.setSession(session);
        o.setWaiter(waiter);
        o.setRoundNumber(round);
        o.setStatus(Order.Status.SUBMITTED);
        o.setSubmittedAt(java.time.Instant.now());
        return persist(o);
    }

    OrderItem item(Order order, MenuItem menu, OrderItem.Status status) {
        OrderItem i = new OrderItem();
        i.setOrder(order);
        i.setMenuItem(menu);
        i.setItemName(menu.getName());
        i.setUnit("dish");
        i.setQuantity(BigDecimal.ONE);
        i.setUnitPrice(menu.getPrice());
        i.setStatus(status);
        return persist(i);
    }

    <T> T persist(T entity) {
        T saved =
                tx.execute(
                        s -> {
                            T merged = em.merge(entity);
                            em.flush();
                            return merged;
                        });

        for (int i = 0; i < created.size(); i++) {
            Object c = created.get(i);
            if (c.getClass() == saved.getClass() && idOf(c).equals(idOf(saved))) {
                created.set(i, saved);
                return saved;
            }
        }
        created.add(saved);
        return saved;
    }

    static Object idOf(Object e) {
        try {
            return e.getClass().getMethod("getId").invoke(e);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    Employee saveUser(String email, Role role) {
        Employee u = new Employee();
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(PASSWORD));
        u.setFullName(role.name() + " " + email);
        u.setRole(role);
        u = users.save(u);
        testUsers.add(u);
        return u;
    }

    String bearer(String email) throws Exception {
        String body =
                mockMvc.perform(
                                request(HttpMethod.POST, "/api/v1/auth/login")
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
