package com.vertex.restosphere;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.entity.MenuCategory;
import com.vertex.restosphere.entity.MenuItem;
import com.vertex.restosphere.entity.User;
import com.vertex.restosphere.repository.AuditLogRepository;
import com.vertex.restosphere.repository.MenuCategoryRepository;
import com.vertex.restosphere.repository.MenuItemRepository;
import com.vertex.restosphere.repository.RefreshTokenRepository;
import com.vertex.restosphere.repository.UserRepository;
import java.math.BigDecimal;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ManagerMenuCategoryApiIntegrationTests {

    private static final String MANAGER_EMAIL = "m1-manager@restosphere.test";
    private static final String PASSWORD = "correct-horse-battery";
    private static final String URL = "/api/v1/menu/categories";
    private static final String SEED_NAME = "Cua & tôm";

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository users;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired AuditLogRepository auditLogs;
    @Autowired MenuCategoryRepository categories;
    @Autowired MenuItemRepository items;
    @Autowired PasswordEncoder passwordEncoder;

    User manager;
    String token;
    final List<UUID> created = new ArrayList<>();
    MenuCategory fallbackSeed;
    MenuItem fallbackItem;

    @BeforeEach
    void setUp() throws Exception {
        cleanUsers();
        manager = saveUser(MANAGER_EMAIL, Role.MANAGER);
        token = bearer(MANAGER_EMAIL);
    }

    @AfterEach
    void tearDown() {
        // Chi xoa danh muc test tu tao; giu nguyen danh muc mau cua seed.
        created.forEach(id -> categories.findById(id).ifPresent(categories::delete));
        created.clear();
        if (fallbackItem != null) {
            items.delete(fallbackItem);
            categories.delete(fallbackSeed);
            fallbackItem = null;
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
        String id =
                create(
                                token,
                                "{\"name\":\"  M1 Sinh tố "
                                        + " \",\"sortOrder\":11,\"visible\":true,\"group\":\"drink\"}")
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.name").value("M1 Sinh tố"))
                        .andExpect(jsonPath("$.group").value("drink"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        UUID categoryId = UUID.fromString(JsonPath.read(id, "$.id"));
        assertThat(auditLogs.findAll())
                .singleElement()
                .satisfies(
                        a -> {
                            assertThat(a.getAction()).isEqualTo("CATEGORY_CREATED");
                            assertThat(a.getTargetType()).isEqualTo("MENU_CATEGORY");
                            assertThat(a.getTargetId()).isEqualTo(categoryId);
                            assertThat(a.getDescription()).isEqualTo("Thêm danh mục M1 Sinh tố");
                            assertThat(a.getActor().getId()).isEqualTo(manager.getId());
                        });

        saveUser("m1-admin@restosphere.test", Role.ADMIN);
        create(
                        bearer("m1-admin@restosphere.test"),
                        "{\"name\":\"M1 Admin\",\"sortOrder\":12,\"visible\":true}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.group").value("food"));
    }

    @Test
    void duplicateNameIgnoringCaseIsConflict() throws Exception {
        seedWithItem();
        create(token, "{\"name\":\"CUA & TÔM\",\"sortOrder\":1,\"visible\":true}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Category name already exists"));

        // Sua danh muc khac sang ten trung cung 409; sua chinh no (doi hoa thuong) thi duoc.
        UUID id = createId("{\"name\":\"M1 Rieng\",\"sortOrder\":1,\"visible\":true}");
        update(id, "{\"name\":\"cua & tôm\",\"sortOrder\":1,\"visible\":true}")
                .andExpect(status().isConflict());
        update(id, "{\"name\":\"M1 RIENG\",\"sortOrder\":1,\"visible\":true}")
                .andExpect(status().isOk());
    }

    @Test
    void updateAuditsChangesOnlyAndKeepsGroupWhenMissing() throws Exception {
        UUID id =
                createId(
                        "{\"name\":\"M1"
                                + " Cu\",\"sortOrder\":20,\"visible\":true,\"group\":\"food\"}");
        auditLogs.deleteAll();

        String body =
                "{\"name\":\"M1 Moi\",\"sortOrder\":21,\"visible\":false,\"group\":\"drink\"}";
        update(id, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("M1 Moi"))
                .andExpect(jsonPath("$.sortOrder").value(21))
                .andExpect(jsonPath("$.visible").value(false))
                .andExpect(jsonPath("$.group").value("drink"));
        assertThat(auditLogs.findAll())
                .singleElement()
                .satisfies(
                        a -> {
                            assertThat(a.getAction()).isEqualTo("CATEGORY_UPDATED");
                            assertThat(a.getTargetId()).isEqualTo(id);
                            assertThat(a.getDescription()).isEqualTo("Cập nhật danh mục M1 Moi");
                        });

        // Gui lai y nguyen: khong ghi log moi.
        update(id, body).andExpect(status().isOk());
        assertThat(auditLogs.count()).isEqualTo(1);

        // Khong gui group: giu nhom cu (drink), van coi la khong doi.
        update(id, "{\"name\":\"M1 Moi\",\"sortOrder\":21,\"visible\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.group").value("drink"));
        assertThat(auditLogs.count()).isEqualTo(1);

        update(UUID.randomUUID(), body).andExpect(status().isNotFound());
    }

    @Test
    void deleteEmptyCategoryButNotOneWithItems() throws Exception {
        UUID id = createId("{\"name\":\"M1 Xoa\",\"sortOrder\":30,\"visible\":true}");
        auditLogs.deleteAll();
        mockMvc.perform(delete(URL + "/" + id).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNoContent());
        assertThat(categories.findById(id)).isEmpty();
        assertThat(auditLogs.findAll())
                .singleElement()
                .satisfies(
                        a -> {
                            assertThat(a.getAction()).isEqualTo("CATEGORY_DELETED");
                            assertThat(a.getTargetId()).isEqualTo(id);
                            assertThat(a.getDescription()).isEqualTo("Xóa danh mục M1 Xoa");
                        });

        MenuCategory seed = seedWithItem();
        mockMvc.perform(delete(URL + "/" + seed.getId()).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Category still has menu items"));
        assertThat(categories.findById(seed.getId())).isPresent();
        mockMvc.perform(
                        delete(URL + "/" + UUID.randomUUID())
                                .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidBodiesAreRejected() throws Exception {
        long before = categories.count();
        for (String body :
                new String[] {
                    "{\"name\":\"   \",\"sortOrder\":1,\"visible\":true}",
                    "{\"name\":\"" + "x".repeat(101) + "\",\"sortOrder\":1,\"visible\":true}",
                    "{\"name\":\"M1 Sai\",\"sortOrder\":-1,\"visible\":true}",
                    "{\"name\":\"M1 Sai\",\"sortOrder\":1000,\"visible\":true}",
                    "{\"name\":\"M1 Sai\",\"sortOrder\":1,\"visible\":true,\"group\":\"snack\"}",
                    "{\"name\":\"M1 Sai\",\"sortOrder\":1}"
                }) {
            create(token, body).andExpect(status().isBadRequest());
        }
        assertThat(categories.count()).isEqualTo(before);
        assertThat(auditLogs.count()).isZero();
    }

    @Test
    void publicListHidesHiddenAndManagerSeesAll() throws Exception {
        UUID hidden = createId("{\"name\":\"M1 An\",\"sortOrder\":40,\"visible\":false}");
        UUID shown = createId("{\"name\":\"M1 Hien\",\"sortOrder\":40,\"visible\":true}");

        String publicBody =
                mockMvc.perform(get(URL))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        List<Map<String, Object>> publicList = JsonPath.read(publicBody, "$");
        assertThat(publicList)
                .allSatisfy(
                        c -> {
                            assertThat(c.get("visible")).isEqualTo(true);
                            assertThat(c.get("group")).isIn("food", "drink");
                        });
        assertThat(publicList)
                .extracting(c -> c.get("id"))
                .contains(shown.toString())
                .doesNotContain(hidden.toString());

        String allBody =
                mockMvc.perform(get(URL + "?all=true").header(HttpHeaders.AUTHORIZATION, token))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        List<String> names = JsonPath.read(allBody, "$[*].name");
        assertThat(names).contains("M1 An");
        // Cung sortOrder 40 thi sap theo ten: "M1 An" truoc "M1 Hien".
        assertThat(names.indexOf("M1 An")).isLessThan(names.indexOf("M1 Hien"));
    }

    @Test
    void accessControl() throws Exception {
        String body = "{\"name\":\"M1 Cam\",\"sortOrder\":1,\"visible\":true}";
        saveUser("m1-waiter@restosphere.test", Role.WAITER);
        create(bearer("m1-waiter@restosphere.test"), body).andExpect(status().isForbidden());
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(
                        post(URL)
                                .with(
                                        jwt().jwt(j -> j.subject("not-a-uuid"))
                                                .authorities(
                                                        new SimpleGrantedAuthority("ROLE_MANAGER")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isUnauthorized());

        // Manager bi khoa trong DB nhung token con han.
        manager.setEnabled(false);
        users.save(manager);
        create(token, body).andExpect(status().isForbidden());
        mockMvc.perform(get(URL + "?all=true").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
        assertThat(categories.findAll()).noneMatch(c -> c.getName().equals("M1 Cam"));
    }

    /** Danh muc mau "Cua & tôm" co mon; test catalog co the da xoa seed nen tu tao lai khi can. */
    private MenuCategory seedWithItem() {
        return categories.findAll().stream()
                .filter(c -> c.getName().equals(SEED_NAME) && items.existsByCategoryId(c.getId()))
                .findFirst()
                .orElseGet(
                        () -> {
                            fallbackSeed = new MenuCategory();
                            fallbackSeed.setName(SEED_NAME);
                            fallbackSeed.setSortOrder(2);
                            categories.save(fallbackSeed);
                            fallbackItem = new MenuItem();
                            fallbackItem.setName("M1 Cua hap");
                            fallbackItem.setCategory(fallbackSeed);
                            fallbackItem.setPrice(new BigDecimal("890000"));
                            items.save(fallbackItem);
                            return fallbackSeed;
                        });
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
        String json = r.andReturn().getResponse().getContentAsString();
        if (r.andReturn().getResponse().getStatus() == 201) {
            created.add(UUID.fromString(JsonPath.read(json, "$.id")));
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

    private User saveUser(String email, Role role) {
        User u = new User();
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
