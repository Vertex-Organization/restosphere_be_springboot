package com.vertex.restosphere;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.entity.Employee;
import com.vertex.restosphere.entity.RestaurantSettings;
import com.vertex.restosphere.repository.AuditLogRepository;
import com.vertex.restosphere.repository.EmployeeRepository;
import com.vertex.restosphere.repository.RefreshTokenRepository;
import com.vertex.restosphere.repository.RestaurantSettingsRepository;
import java.util.LinkedHashMap;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AdminRestaurantApiIntegrationTests {

    private static final String ADMIN_EMAIL = "a3-admin@restosphere.test";
    private static final String PASSWORD = "correct-horse-battery";
    private static final String URL = "/api/v1/admin/restaurant";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired MockMvc mockMvc;
    @Autowired EmployeeRepository users;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired AuditLogRepository auditLogs;
    @Autowired RestaurantSettingsRepository settingsRepo;
    @Autowired PasswordEncoder passwordEncoder;

    Employee admin;
    RestaurantSettings original;

    @BeforeEach
    void setUp() {
        cleanUsers();
        // restaurant_settings chi co 1 dong dung chung: luu lai de khoi phuc sau moi test.
        original =
                settingsRepo
                        .findBySingletonKey(1)
                        .orElseGet(
                                () -> {
                                    RestaurantSettings s = new RestaurantSettings();
                                    s.setName("RestoSphere");
                                    return settingsRepo.save(s);
                                });
        admin = saveUser(ADMIN_EMAIL, Role.ADMIN);
    }

    @AfterEach
    void tearDown() {
        RestaurantSettings s = settingsRepo.findBySingletonKey(1).orElseThrow();
        s.setName(original.getName());
        s.setDescription(original.getDescription());
        s.setAddress(original.getAddress());
        s.setPhone(original.getPhone());
        s.setEmail(original.getEmail());
        s.setOpeningHours(original.getOpeningHours());
        s.setFacebook(original.getFacebook());
        s.setInstagram(original.getInstagram());
        s.setTiktok(original.getTiktok());
        s.setLogoUrl(original.getLogoUrl());
        s.setCoverUrl(original.getCoverUrl());
        settingsRepo.save(s);
        cleanUsers();
    }

    void cleanUsers() {
        // audit_logs va refresh_tokens tham chieu users nen xoa truoc.
        auditLogs.deleteAll();
        refreshTokens.deleteAll();
        users.deleteAll();
    }

    @Test
    void adminGetsAllFieldsWithoutNulls() throws Exception {
        String body =
                mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_EMAIL)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        Map<String, Object> json = JsonPath.read(body, "$");
        assertThat(json)
                .containsOnlyKeys(
                        "name",
                        "description",
                        "address",
                        "hotline",
                        "email",
                        "openingHours",
                        "facebook",
                        "instagram",
                        "tiktok",
                        "logoUrl",
                        "coverUrl");
        assertThat(json.values()).doesNotContainNull();
    }

    @Test
    void updateSavesChangesAuditsOnceAndOldWorkspaceSeesNewName() throws Exception {
        String token = bearer(ADMIN_EMAIL);
        Map<String, String> body = profile();
        body.put("name", "  The Sea's Crown  ");
        body.put("hotline", "0901234567");
        body.put("facebook", "https://facebook.com/seascrown");

        putProfile(token, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("The Sea's Crown"));
        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.name").value("The Sea's Crown"))
                .andExpect(jsonPath("$.hotline").value("0901234567"))
                .andExpect(jsonPath("$.facebook").value("https://facebook.com/seascrown"));
        assertThat(auditLogs.findAll())
                .singleElement()
                .satisfies(
                        a -> {
                            assertThat(a.getAction()).isEqualTo("RESTAURANT_UPDATED");
                            assertThat(a.getTargetType()).isEqualTo("SETTINGS");
                            assertThat(a.getTargetId()).isEqualTo(original.getId());
                            assertThat(a.getDescription())
                                    .isEqualTo(
                                            "Restaurant profile updated: name, hotline, facebook");
                        });

        // Gui lai dung du lieu cu: khong ghi them log.
        putProfile(token, body).andExpect(status().isOk());
        assertThat(auditLogs.count()).isEqualTo(1);

        // API workspace cu doc cung bang nen thay ten moi.
        saveUser("a3-cashier@restosphere.test", Role.CASHIER);
        mockMvc.perform(
                        get("/api/v1/workspaces/cashier")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        bearer("a3-cashier@restosphere.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.config.restaurantName").value("The Sea's Crown"));
    }

    @Test
    void blankHotlineIsStoredAsNullAndReturnedAsEmpty() throws Exception {
        String token = bearer(ADMIN_EMAIL);
        Map<String, String> body = profile();
        body.put("hotline", "0901234567");
        putProfile(token, body).andExpect(status().isOk());

        body.put("hotline", "  ");
        putProfile(token, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hotline").value(""));
        assertThat(settingsRepo.findBySingletonKey(1).orElseThrow().getPhone()).isNull();
    }

    @Test
    void invalidInputIsRejectedAndNothingChanges() throws Exception {
        String token = bearer(ADMIN_EMAIL);
        String before =
                mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, token))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        Map<String, String[]> cases =
                Map.of(
                        "name", new String[] {"A", "x".repeat(151)},
                        "email", new String[] {"not-an-email"},
                        "facebook", new String[] {"http://facebook.com/x"},
                        "hotline", new String[] {"1".repeat(31)});
        for (var c : cases.entrySet()) {
            for (String value : c.getValue()) {
                Map<String, String> body = profile();
                body.put(c.getKey(), value);
                putProfile(token, body).andExpect(status().isBadRequest());
            }
        }

        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(r -> assertThat(r.getResponse().getContentAsString()).isEqualTo(before));
        assertThat(auditLogs.count()).isZero();
    }

    @Test
    void accessControl() throws Exception {
        saveUser("a3-manager@restosphere.test", Role.MANAGER);
        mockMvc.perform(
                        get(URL).header(
                                        HttpHeaders.AUTHORIZATION,
                                        bearer("a3-manager@restosphere.test")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());

        String adminToken = bearer(ADMIN_EMAIL);
        admin.setEnabled(false);
        users.save(admin);
        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isForbidden());
        putProfile(adminToken, profile()).andExpect(status().isForbidden());
    }

    /** Body hop le voi 11 field, mac dinh rong tru name. */
    private Map<String, String> profile() {
        Map<String, String> body = new LinkedHashMap<>();
        for (String f :
                new String[] {
                    "description",
                    "address",
                    "hotline",
                    "email",
                    "openingHours",
                    "facebook",
                    "instagram",
                    "tiktok",
                    "logoUrl",
                    "coverUrl"
                }) {
            body.put(f, "");
        }
        body.put("name", "A3 Restaurant");
        return body;
    }

    private ResultActions putProfile(String token, Map<String, String> body) throws Exception {
        return mockMvc.perform(
                put(URL).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(body)));
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
