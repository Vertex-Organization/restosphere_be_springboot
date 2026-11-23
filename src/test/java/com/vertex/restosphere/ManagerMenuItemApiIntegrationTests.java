package com.vertex.restosphere;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.entity.Area;
import com.vertex.restosphere.entity.Bill;
import com.vertex.restosphere.entity.Employee;
import com.vertex.restosphere.entity.MenuCategory;
import com.vertex.restosphere.entity.MenuItem;
import com.vertex.restosphere.entity.Order;
import com.vertex.restosphere.entity.OrderItem;
import com.vertex.restosphere.entity.RestaurantTable;
import com.vertex.restosphere.entity.TableSession;
import com.vertex.restosphere.repository.AreaRepository;
import com.vertex.restosphere.repository.AuditLogRepository;
import com.vertex.restosphere.repository.EmployeeRepository;
import com.vertex.restosphere.repository.MenuCategoryRepository;
import com.vertex.restosphere.repository.MenuItemRepository;
import com.vertex.restosphere.repository.RefreshTokenRepository;
import com.vertex.restosphere.repository.RestaurantTableRepository;
import com.vertex.restosphere.repository.TableSessionRepository;
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
class ManagerMenuItemApiIntegrationTests {

    private static final String MANAGER_EMAIL = "m2-manager@restosphere.test";
    private static final String PASSWORD = "correct-horse-battery";
    private static final String URL = "/api/v1/menu/items";

    @Autowired MockMvc mockMvc;
    @Autowired EmployeeRepository users;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired AuditLogRepository auditLogs;
    @Autowired MenuCategoryRepository categories;
    @Autowired MenuItemRepository items;
    @Autowired TableSessionRepository tableSessions;
    @Autowired RestaurantTableRepository tables;
    @Autowired AreaRepository areas;
    @Autowired EntityManager em;
    @Autowired TransactionTemplate tx;
    @Autowired PasswordEncoder passwordEncoder;

    Employee manager;
    String token;
    MenuCategory visible;
    MenuCategory hidden;
    final List<UUID> createdItems = new ArrayList<>();
    // Du lieu don hang tu tao, xoa theo thu tu khoa ngoai.
    final List<Object> usage = new ArrayList<>();
    Area createdArea;
    RestaurantTable createdTable;

    @BeforeEach
    void setUp() throws Exception {
        cleanUsers();
        manager = saveUser(MANAGER_EMAIL, Role.MANAGER);
        token = bearer(MANAGER_EMAIL);
        visible = saveCategory("M2 Hien", true);
        hidden = saveCategory("M2 An", false);
    }

    @AfterEach
    void tearDown() {
        // Chi xoa du lieu test tu tao; mon/danh muc mau cua seed giu nguyen.
        tx.executeWithoutResult(
                s -> {
                    for (int i = usage.size() - 1; i >= 0; i--) {
                        em.remove(em.merge(usage.get(i)));
                    }
                });
        usage.clear();
        createdItems.forEach(id -> items.findById(id).ifPresent(items::delete));
        createdItems.clear();
        categories.delete(visible);
        categories.delete(hidden);
        if (createdTable != null) {
            tables.delete(createdTable);
            areas.delete(createdArea);
            createdTable = null;
        }
        cleanUsers();
    }

    void cleanUsers() {
        // audit_logs va refresh_tokens tham chieu users nen xoa truoc.
        auditLogs.deleteAll();
        refreshTokens.deleteAll();
        users.deleteAll();
    }

    @Test
    void managerAndAdminCreateWithAudit() throws Exception {
        String json =
                create(
                                token,
                                body(
                                        "  M2 Cua cân  ",
                                        visible.getId(),
                                        250000,
                                        ",\"unit\":\"kg\",\"description\":\"  \""))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.name").value("M2 Cua cân"))
                        .andExpect(jsonPath("$.status").value("AVAILABLE"))
                        .andExpect(jsonPath("$.unit").value("kg"))
                        .andExpect(jsonPath("$.categoryName").value("M2 Hien"))
                        .andExpect(jsonPath("$.description").doesNotExist())
                        .andExpect(jsonPath("$.updatedAt").isString())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(json, "$.id"));
        assertThat(auditLogs.findAll())
                .singleElement()
                .satisfies(
                        a -> {
                            assertThat(a.getAction()).isEqualTo("MENU_ITEM_CREATED");
                            assertThat(a.getTargetType()).isEqualTo("MENU_ITEM");
                            assertThat(a.getTargetId()).isEqualTo(id);
                            assertThat(a.getDescription()).isEqualTo("Thêm món M2 Cua cân");
                            assertThat(a.getActor().getId()).isEqualTo(manager.getId());
                        });

        saveUser("m2-admin@restosphere.test", Role.ADMIN);
        create(bearer("m2-admin@restosphere.test"), body("M2 Admin", visible.getId(), 1000, ""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unit").value("portion"));
    }

    @Test
    void updateAuditsPriceChangeAndKeepsMissingStatusUnit() throws Exception {
        UUID id =
                createId(
                        body(
                                "M2 Muc",
                                visible.getId(),
                                250000,
                                ",\"unit\":\"kg\",\"status\":\"SOLD_OUT\""));
        auditLogs.deleteAll();

        String changed =
                body("M2 Muc", visible.getId(), 270000, ",\"unit\":\"kg\",\"status\":\"SOLD_OUT\"");
        update(id, changed).andExpect(status().isOk()).andExpect(jsonPath("$.price").value(270000));
        assertThat(auditLogs.findAll())
                .singleElement()
                .satisfies(
                        a -> {
                            assertThat(a.getAction()).isEqualTo("MENU_ITEM_UPDATED");
                            assertThat(a.getTargetId()).isEqualTo(id);
                            assertThat(a.getDescription())
                                    .isEqualTo("Cập nhật món M2 Muc (giá 250.000đ → 270.000đ)");
                        });

        // Gui lai y nguyen: khong ghi log moi.
        update(id, changed).andExpect(status().isOk());
        assertThat(auditLogs.count()).isEqualTo(1);

        // Khong gui status/unit: giu SOLD_OUT/kg, va van la khong doi.
        update(id, body("M2 Muc", visible.getId(), 270000, ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SOLD_OUT"))
                .andExpect(jsonPath("$.unit").value("kg"));
        assertThat(auditLogs.count()).isEqualTo(1);

        // Doi ten khong doi gia: khong co phan "(gia ...)".
        update(id, body("M2 Muc nuong", visible.getId(), 270000, "")).andExpect(status().isOk());
        assertThat(auditLogs.findAll())
                .anyMatch(a -> a.getDescription().equals("Cập nhật món M2 Muc nuong"));

        update(UUID.randomUUID(), changed).andExpect(status().isNotFound());
    }

    @Test
    void hiddenCategoryRules() throws Exception {
        create(token, body("M2 Lau", hidden.getId(), 1000, ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Category is hidden"));

        UUID id = createId(body("M2 Lau", visible.getId(), 1000, ""));
        // Mon sau do bi an theo danh muc: van sua gia duoc neu khong doi danh muc.
        visible.setVisible(false);
        categories.save(visible);
        update(id, body("M2 Lau", visible.getId(), 2000, "")).andExpect(status().isOk());

        visible.setVisible(true);
        categories.save(visible);
        update(id, body("M2 Lau", hidden.getId(), 2000, ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Category is hidden"));
        assertThat(items.findById(id).orElseThrow().getCategory().getId())
                .isEqualTo(visible.getId());
    }

    @Test
    void deleteUnusedButNotUsedItems() throws Exception {
        UUID id = createId(body("M2 Xoa", visible.getId(), 1000, ""));
        auditLogs.deleteAll();
        mockMvc.perform(delete(URL + "/" + id).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNoContent());
        assertThat(items.findById(id)).isEmpty();
        assertThat(auditLogs.findAll())
                .singleElement()
                .satisfies(
                        a -> {
                            assertThat(a.getAction()).isEqualTo("MENU_ITEM_DELETED");
                            assertThat(a.getTargetId()).isEqualTo(id);
                            assertThat(a.getDescription()).isEqualTo("Xóa món M2 Xoa");
                        });

        UUID ordered = createId(body("M2 Da goi", visible.getId(), 1000, ""));
        UUID drafted = createId(body("M2 Nhap", visible.getId(), 1000, ""));
        useInOrder(ordered, drafted);
        for (UUID used : List.of(ordered, drafted)) {
            mockMvc.perform(delete(URL + "/" + used).header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isConflict())
                    .andExpect(
                            jsonPath("$.detail")
                                    .value("Menu item is in use, set it to DISCONTINUED instead"));
            assertThat(items.findById(used)).isPresent();
        }
        mockMvc.perform(
                        delete(URL + "/" + UUID.randomUUID())
                                .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidBodiesAreRejected() throws Exception {
        UUID cat = visible.getId();
        long before = items.count();
        for (String body :
                new String[] {
                    body("   ", cat, 1000, ""),
                    body("x".repeat(151), cat, 1000, ""),
                    body("M2 Sai", cat, -1, ""),
                    "{\"name\":\"M2 Sai\",\"categoryId\":\"" + cat + "\",\"price\":12.5}",
                    body("M2 Sai", cat, 1000, ",\"unit\":\"box\""),
                    body("M2 Sai", cat, 1000, ",\"status\":\"HIDDEN\""),
                    body("M2 Sai", cat, 1000, ",\"imageUrl\":\"http://a.b/c.png\""),
                    body("M2 Sai", cat, 1000, ",\"imageUrl\":\"//evil.com/x.png\"")
                }) {
            create(token, body).andExpect(status().isBadRequest());
        }
        create(token, body("M2 Sai", UUID.randomUUID(), 1000, "")).andExpect(status().isNotFound());
        assertThat(items.count()).isEqualTo(before);
        assertThat(auditLogs.count()).isZero();

        // Anh hop le: rong, duong dan noi bo, https.
        create(token, body("M2 Anh 1", cat, 1000, ",\"imageUrl\":\"\""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageUrl").doesNotExist());
        create(token, body("M2 Anh 2", cat, 1000, ",\"imageUrl\":\"/images/a.png\""))
                .andExpect(status().isCreated());
        create(token, body("M2 Anh 3", cat, 1000, ",\"imageUrl\":\"https://cdn.x/a.png\""))
                .andExpect(status().isCreated());
    }

    @Test
    void publicListHidesDiscontinuedAndHiddenCategory() throws Exception {
        UUID shown = createId(body("M2 Ban", visible.getId(), 1000, ""));
        UUID stopped =
                createId(body("M2 Ngung", visible.getId(), 1000, ",\"status\":\"DISCONTINUED\""));
        UUID inHidden = createId(body("M2 Trong an", visible.getId(), 1000, ""));
        // Chuyen mon sang danh muc an qua DB (REST khong cho chuyen).
        MenuItem m = items.findById(inHidden).orElseThrow();
        m.setCategory(hidden);
        items.save(m);

        String publicBody =
                mockMvc.perform(get(URL))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        List<Map<String, Object>> list = JsonPath.read(publicBody, "$");
        assertThat(list)
                .allSatisfy(
                        i -> {
                            assertThat(i.get("status")).isNotEqualTo("DISCONTINUED");
                            assertThat(i).containsKey("updatedAt");
                        });
        assertThat(list)
                .extracting(i -> i.get("id"))
                .contains(shown.toString())
                .doesNotContain(stopped.toString(), inHidden.toString());

        String allBody =
                mockMvc.perform(get(URL + "?all=true").header(HttpHeaders.AUTHORIZATION, token))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        List<String> ids = JsonPath.read(allBody, "$[*].id");
        assertThat(ids).contains(shown.toString(), stopped.toString(), inHidden.toString());
        // Cung danh muc thi sap theo ten: "M2 Ban" truoc "M2 Ngung".
        assertThat(ids.indexOf(shown.toString())).isLessThan(ids.indexOf(stopped.toString()));
    }

    @Test
    void accessControl() throws Exception {
        String body = body("M2 Cam", visible.getId(), 1000, "");
        saveUser("m2-waiter@restosphere.test", Role.WAITER);
        create(bearer("m2-waiter@restosphere.test"), body).andExpect(status().isForbidden());
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());

        // Manager bi khoa trong DB nhung token con han.
        manager.setEnabled(false);
        users.save(manager);
        create(token, body).andExpect(status().isForbidden());
        mockMvc.perform(get(URL + "?all=true").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
        assertThat(items.findAll()).noneMatch(i -> i.getName().equals("M2 Cam"));
    }

    /** Dua mon vao order_items (ordered) va order_draft (drafted) cua mot phien ban tu tao. */
    private void useInOrder(UUID ordered, UUID drafted) {
        Employee waiter = saveUser("m2-waiter2@restosphere.test", Role.WAITER);
        tx.executeWithoutResult(
                s -> {
                    Bill bill = new Bill();
                    bill.setCode("M2-" + UUID.randomUUID().toString().substring(0, 8));
                    em.persist(bill);
                    TableSession session = new TableSession();
                    session.setBill(bill);
                    session.setTable(em.merge(anyTable()));
                    session.setGuestCount(2);
                    session.setStartedAt(Instant.now());
                    em.persist(session);
                    Order order = new Order();
                    order.setSession(session);
                    order.setWaiter(em.merge(waiter));
                    order.setRoundNumber(1);
                    order.setStatus(Order.Status.SUBMITTED);
                    order.setSubmittedAt(java.time.Instant.now());
                    em.persist(order);
                    OrderItem oi = new OrderItem();
                    oi.setOrder(order);
                    oi.setMenuItem(em.find(MenuItem.class, ordered));
                    oi.setItemName("M2 Da goi");
                    oi.setUnit("portion");
                    oi.setQuantity(BigDecimal.ONE);
                    oi.setUnitPrice(new BigDecimal("1000"));
                    em.persist(oi);
                    Order cart = new Order();
                    cart.setSession(session);
                    cart.setWaiter(order.getWaiter());
                    em.persist(cart);
                    OrderItem d = new OrderItem();
                    d.setOrder(cart);
                    d.setMenuItem(em.find(MenuItem.class, drafted));
                    d.setItemName("M2 Nhap");
                    d.setUnit("portion");
                    d.setQuantity(BigDecimal.ONE);
                    d.setUnitPrice(new BigDecimal("1000"));
                    d.setStatus(OrderItem.Status.DRAFT);
                    em.persist(d);
                    usage.addAll(List.of(bill, session, order, oi, cart, d));
                });
    }

    // Dung ban seed neu con; test catalog co the da xoa het ban nen tu tao khi can.
    private RestaurantTable anyTable() {
        return tables.findAll().stream()
                .findFirst()
                .orElseGet(
                        () -> {
                            createdArea = new Area();
                            createdArea.setName("m2-area");
                            areas.save(createdArea);
                            createdTable = new RestaurantTable();
                            createdTable.setArea(createdArea);
                            createdTable.setCode("M2-T1");
                            createdTable.setCapacity(4);
                            return tables.save(createdTable);
                        });
    }

    private MenuCategory saveCategory(String name, boolean isVisible) {
        MenuCategory c = new MenuCategory();
        c.setName(name);
        c.setSortOrder(900);
        c.setVisible(isVisible);
        return categories.save(c);
    }

    private static String body(String name, UUID categoryId, long price, String extra) {
        return "{\"name\":\"%s\",\"categoryId\":\"%s\",\"price\":%d%s}"
                .formatted(name, categoryId, price, extra);
    }

    private UUID createId(String body) throws Exception {
        String json =
                create(token, body)
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return UUID.fromString(JsonPath.read(json, "$.id"));
    }

    private ResultActions create(String bearer, String body) throws Exception {
        ResultActions r =
                mockMvc.perform(
                        post(URL)
                                .header(HttpHeaders.AUTHORIZATION, bearer)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body));
        if (r.andReturn().getResponse().getStatus() == 201) {
            createdItems.add(
                    UUID.fromString(
                            JsonPath.read(
                                    r.andReturn().getResponse().getContentAsString(), "$.id")));
        }
        return r;
    }

    private ResultActions update(UUID id, String body) throws Exception {
        return mockMvc.perform(
                put(URL + "/" + id)
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
