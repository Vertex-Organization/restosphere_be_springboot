package com.vertex.restosphere;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.entity.AuditLog;
import com.vertex.restosphere.entity.RestaurantSettings;
import com.vertex.restosphere.entity.User;
import com.vertex.restosphere.repository.AuditLogRepository;
import com.vertex.restosphere.repository.RefreshTokenRepository;
import com.vertex.restosphere.repository.RestaurantSettingsRepository;
import com.vertex.restosphere.repository.UserRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AdminAuditLogApiIntegrationTests {

    private static final String ADMIN_EMAIL = "a5-admin@restosphere.test";
    private static final String PASSWORD = "correct-horse-battery";
    private static final String URL = "/api/v1/admin/audit-logs";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository users;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired AuditLogRepository auditLogs;
    @Autowired RestaurantSettingsRepository settingsRepo;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;

    User admin;
    String token;
    RestaurantSettings original;

    @BeforeEach
    void setUp() throws Exception {
        clean();
        // Dong settings dung chung: luu lai cac field test se doi, ep mui gio VN cho case loc ngay.
        original = settingsRepo.findBySingletonKey(1).orElseGet(() -> {
            RestaurantSettings s = new RestaurantSettings();
            s.setName("RestoSphere");
            return settingsRepo.save(s);
        });
        RestaurantSettings s = settingsRepo.findBySingletonKey(1).orElseThrow();
        s.setTimezone("Asia/Ho_Chi_Minh");
        settingsRepo.save(s);
        admin = saveUser(ADMIN_EMAIL, "Quản trị demo", Role.ADMIN);
        token = bearer(ADMIN_EMAIL);
    }

    @AfterEach
    void tearDown() {
        RestaurantSettings s = settingsRepo.findBySingletonKey(1).orElseThrow();
        s.setName(original.getName());
        s.setBufferMinutes(original.getBufferMinutes());
        s.setTimezone(original.getTimezone());
        settingsRepo.save(s);
        clean();
    }

    void clean() {
        // audit_logs va refresh_tokens tham chieu users nen xoa truoc.
        auditLogs.deleteAll();
        refreshTokens.deleteAll();
        users.deleteAll();
    }

    @Test
    void onlyAdminActionsNewestFirstWithLimit() throws Exception {
        log(admin, "ROLE_CHANGED", "r1", "2026-11-10T01:00:00Z");
        log(admin, "submit", "workspace", "2026-11-10T02:00:00Z");
        log(admin, "ACCOUNT_LOCKED", "l1", "2026-11-10T03:00:00Z");
        log(null, "SETTINGS_UPDATED", "s1", "2026-11-10T04:00:00Z");

        mockMvc.perform(list(""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[*].target").value(org.hamcrest.Matchers.contains("s1", "l1", "r1")))
                .andExpect(jsonPath("$[0].actor").value("System"))
                .andExpect(jsonPath("$[0].at").value("2026-11-10T04:00:00Z"))
                .andExpect(jsonPath("$[0].result").value("SUCCESS"))
                .andExpect(jsonPath("$[1].actor").value("Quản trị demo"))
                .andExpect(jsonPath("$[1].action").value("ACCOUNT_LOCKED"));
        mockMvc.perform(list("limit=2"))
                .andExpect(jsonPath("$[*].target").value(org.hamcrest.Matchers.contains("s1", "l1")));
        String body = mockMvc.perform(list("")).andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> rows = JsonPath.read(body, "$");
        assertThat(rows.getFirst()).containsOnlyKeys("id", "at", "actor", "action", "target", "result");
    }

    @Test
    void filtersByActionAndActorName() throws Exception {
        User other = saveUser("a5-other@restosphere.test", "Nguyễn Văn A", Role.MANAGER);
        log(admin, "ROLE_CHANGED", "r1", "2026-11-10T01:00:00Z");
        log(other, "ROLE_CHANGED", "r2", "2026-11-10T02:00:00Z");
        log(admin, "ACCOUNT_LOCKED", "l1", "2026-11-10T03:00:00Z");
        log(null, "ACCOUNT_UNLOCKED", "u1", "2026-11-10T04:00:00Z");

        mockMvc.perform(list("action=ROLE_CHANGED"))
                .andExpect(jsonPath("$[*].target").value(org.hamcrest.Matchers.contains("r2", "r1")));
        // Khong phan biet hoa thuong, co dau tieng Viet, khop mot phan; log System khong co ten nen khong khop.
        for (String q : new String[] {"quản", "QUẢN TRỊ", "trị demo"}) {
            mockMvc.perform(get(URL).param("actor", q).header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(jsonPath("$[*].target").value(org.hamcrest.Matchers.contains("l1", "r1")));
        }
        mockMvc.perform(get(URL).param("actor", "quản").param("action", "ACCOUNT_LOCKED")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$[*].target").value(org.hamcrest.Matchers.contains("l1")));
        // "%" la ky tu thuong, khong phai wildcard.
        mockMvc.perform(get(URL).param("actor", "%").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void dateFiltersUseRestaurantTimezone() throws Exception {
        log(admin, "ROLE_CHANGED", "late-18", "2026-11-18T16:59:00Z");  // 23:59 ngay 18/11 gio VN
        log(admin, "ROLE_CHANGED", "early-19", "2026-11-18T17:30:00Z"); // 00:30 ngay 19/11 gio VN

        mockMvc.perform(list("from=2026-11-19"))
                .andExpect(jsonPath("$[*].target").value(org.hamcrest.Matchers.contains("early-19")));
        mockMvc.perform(list("to=2026-11-18"))
                .andExpect(jsonPath("$[*].target").value(org.hamcrest.Matchers.contains("late-18")));
        mockMvc.perform(list("from=2026-11-18&to=2026-11-19"))
                .andExpect(jsonPath("$[*].target").value(org.hamcrest.Matchers.contains("early-19", "late-18")));
    }

    @Test
    void settingsAndRestaurantUpdatesAppearWithOwnActions() throws Exception {
        String settings = mockMvc.perform(get("/api/v1/admin/settings").header(HttpHeaders.AUTHORIZATION, token))
                .andReturn().getResponse().getContentAsString();
        Map<String, Object> s = JSON.readValue(settings, Map.class);
        s.put("bufferMinutes", ((Integer) s.get("bufferMinutes")) == 30 ? 31 : 30);
        mockMvc.perform(json(put("/api/v1/admin/settings"), s)).andExpect(status().isOk());

        String restaurant = mockMvc.perform(get("/api/v1/admin/restaurant").header(HttpHeaders.AUTHORIZATION, token))
                .andReturn().getResponse().getContentAsString();
        Map<String, Object> r = JSON.readValue(restaurant, Map.class);
        r.put("name", "A5 Restaurant");
        mockMvc.perform(json(put("/api/v1/admin/restaurant"), r)).andExpect(status().isOk());

        mockMvc.perform(list(""))
                .andExpect(jsonPath("$[*].action")
                        .value(org.hamcrest.Matchers.contains("RESTAURANT_UPDATED", "SETTINGS_UPDATED")))
                .andExpect(jsonPath("$[1].target").value("Settings updated: bufferMinutes"))
                .andExpect(jsonPath("$[1].actor").value("Quản trị demo"));
    }

    @Test
    void invalidQueriesAreRejected() throws Exception {
        for (String q : new String[] {"action=submit", "from=19-11-2026", "to=2026-13-01",
                "from=2026-11-20&to=2026-11-19", "limit=0", "limit=501", "limit=abc", "actor=" + "a".repeat(101)}) {
            mockMvc.perform(list(q)).andExpect(status().isBadRequest());
        }
        mockMvc.perform(list("actor=" + "a".repeat(100))).andExpect(status().isOk());
    }

    @Test
    void accessControl() throws Exception {
        saveUser("a5-manager@restosphere.test", "Manager", Role.MANAGER);
        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, bearer("a5-manager@restosphere.test")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());

        admin.setEnabled(false);
        users.save(admin);
        mockMvc.perform(list("")).andExpect(status().isForbidden());
    }

    /** createdAt khong cap nhat duoc qua JPA (updatable = false) nen sua thang bang SQL. */
    private void log(User actor, String action, String description, String at) {
        AuditLog a = new AuditLog();
        a.setActor(actor);
        a.setAction(action);
        a.setTargetType("submit".equals(action) ? "WORKSPACE" : "USER");
        a.setDescription(description);
        auditLogs.save(a);
        jdbc.update("UPDATE audit_logs SET created_at = ? WHERE id = ?", Timestamp.from(Instant.parse(at)), a.getId());
    }

    private MockHttpServletRequestBuilder list(String query) {
        return get(URL + "?" + query).header(HttpHeaders.AUTHORIZATION, token);
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder req, Object body) {
        return req.header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(body));
    }

    private User saveUser(String email, String fullName, Role role) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(PASSWORD));
        u.setFullName(fullName);
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
