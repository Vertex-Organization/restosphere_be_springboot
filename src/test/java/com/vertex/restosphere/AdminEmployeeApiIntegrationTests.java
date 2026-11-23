package com.vertex.restosphere;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.entity.Employee;
import com.vertex.restosphere.repository.AuditLogRepository;
import com.vertex.restosphere.repository.EmployeeRepository;
import com.vertex.restosphere.repository.RefreshTokenRepository;
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

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AdminEmployeeApiIntegrationTests {

    private static final String ADMIN_EMAIL = "a1-admin@restosphere.test";
    private static final String WAITER_EMAIL = "a1-waiter@restosphere.test";
    private static final String PASSWORD = "correct-horse-battery";
    private static final String USERS = "/api/v1/admin/employees";

    @Autowired MockMvc mockMvc;
    @Autowired EmployeeRepository users;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired AuditLogRepository auditLogs;
    @Autowired PasswordEncoder passwordEncoder;

    Employee admin;
    Employee waiter;

    @BeforeEach
    void setUp() throws InterruptedException {
        clean();
        admin = saveUser(ADMIN_EMAIL, "quan tri", Role.ADMIN);
        Thread.sleep(5); // tranh trung createdAt de kiem tra thu tu sap xep on dinh
        waiter = saveUser(WAITER_EMAIL, "Phục vụ demo", Role.WAITER);
    }

    @AfterEach
    void clean() {
        // audit_logs va refresh_tokens tham chieu users nen xoa truoc.
        auditLogs.deleteAll();
        refreshTokens.deleteAll();
        users.deleteAll();
    }

    @Test
    void adminListsUsersNewestFirst() throws Exception {
        mockMvc.perform(get(USERS).header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(waiter.getId().toString()))
                .andExpect(jsonPath("$[0].fullName").value("Phục vụ demo"))
                .andExpect(jsonPath("$[0].email").value(WAITER_EMAIL))
                .andExpect(jsonPath("$[0].role").value("WAITER"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$[0].initials").value("P"))
                .andExpect(jsonPath("$[1].email").value(ADMIN_EMAIL))
                .andExpect(jsonPath("$[1].initials").value("Q"));
    }

    @Test
    void nonAdminGets403AndAnonymousGets401() throws Exception {
        mockMvc.perform(get(USERS).header(HttpHeaders.AUTHORIZATION, bearer(WAITER_EMAIL)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(USERS)).andExpect(status().isUnauthorized());
    }

    @Test
    void lockRevokesAccessAndUnlockRestoresIt() throws Exception {
        String adminToken = bearer(ADMIN_EMAIL);
        String waiterRefresh = JsonPath.read(loginBody(WAITER_EMAIL), "$.refreshToken");

        setStatus(adminToken, waiter.getId(), "LOCKED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOCKED"));
        assertThat(auditLogs.findAll())
                .singleElement()
                .satisfies(
                        a -> {
                            assertThat(a.getAction()).isEqualTo("ACCOUNT_LOCKED");
                            assertThat(a.getTargetType()).isEqualTo("USER");
                            assertThat(a.getTargetId()).isEqualTo(waiter.getId());
                            assertThat(a.getDescription()).isEqualTo(WAITER_EMAIL);
                        });
        login(WAITER_EMAIL).andExpect(status().isUnauthorized());
        assertThat(refreshTokens.findAll())
                .filteredOn(t -> t.getEmployee().getId().equals(waiter.getId()))
                .isNotEmpty()
                .allSatisfy(t -> assertThat(t.getRevokedAt()).isNotNull());
        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"refreshToken\":\"%s\"}".formatted(waiterRefresh)))
                .andExpect(status().isUnauthorized());

        // Khoa lan nua: khong doi trang thai thi khong ghi them log.
        setStatus(adminToken, waiter.getId(), "LOCKED").andExpect(status().isOk());
        assertThat(auditLogs.count()).isEqualTo(1);

        setStatus(adminToken, waiter.getId(), "ACTIVE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        assertThat(auditLogs.findAll())
                .extracting(a -> a.getAction())
                .containsExactlyInAnyOrder("ACCOUNT_LOCKED", "ACCOUNT_UNLOCKED");
        login(WAITER_EMAIL).andExpect(status().isOk());
    }

    @Test
    void adminCannotLockSelf() throws Exception {
        setStatus(bearer(ADMIN_EMAIL), admin.getId(), "LOCKED")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Cannot change your own access"));
    }

    @Test
    void lockedAdminWithLiveTokenIsDenied() throws Exception {
        String adminToken = bearer(ADMIN_EMAIL);
        admin.setEnabled(false);
        users.save(admin);

        mockMvc.perform(get(USERS).header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isForbidden());
        setStatus(adminToken, waiter.getId(), "LOCKED").andExpect(status().isForbidden());
    }

    @Test
    void invalidBodyAndUnknownUser() throws Exception {
        String adminToken = bearer(ADMIN_EMAIL);
        setStatus(adminToken, waiter.getId(), "ABC").andExpect(status().isBadRequest());
        mockMvc.perform(
                        patch(USERS + "/" + waiter.getId() + "/status")
                                .header(HttpHeaders.AUTHORIZATION, adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
        setStatus(adminToken, UUID.randomUUID(), "LOCKED").andExpect(status().isNotFound());
    }

    private ResultActions setStatus(String token, UUID id, String status) throws Exception {
        return mockMvc.perform(
                patch(USERS + "/" + id + "/status")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"%s\"}".formatted(status)));
    }

    private Employee saveUser(String email, String fullName, Role role) {
        Employee u = new Employee();
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(PASSWORD));
        u.setFullName(fullName);
        u.setRole(role);
        return users.save(u);
    }

    private String bearer(String email) throws Exception {
        return "Bearer " + JsonPath.read(loginBody(email), "$.accessToken");
    }

    private String loginBody(String email) throws Exception {
        return login(email)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private ResultActions login(String email) throws Exception {
        return mockMvc.perform(
                post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"email\":\"%s\",\"password\":\"%s\"}"
                                        .formatted(email, PASSWORD)));
    }
}
