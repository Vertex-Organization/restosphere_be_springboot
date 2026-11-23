package com.vertex.restosphere;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.entity.Area;
import com.vertex.restosphere.entity.Bill;
import com.vertex.restosphere.entity.CashierShift;
import com.vertex.restosphere.entity.Employee;
import com.vertex.restosphere.entity.RestaurantTable;
import com.vertex.restosphere.entity.TableSession;
import com.vertex.restosphere.repository.AreaRepository;
import com.vertex.restosphere.repository.AuditLogRepository;
import com.vertex.restosphere.repository.BillRepository;
import com.vertex.restosphere.repository.CashierShiftRepository;
import com.vertex.restosphere.repository.EmployeeRepository;
import com.vertex.restosphere.repository.RefreshTokenRepository;
import com.vertex.restosphere.repository.RestaurantTableRepository;
import com.vertex.restosphere.repository.TableSessionRepository;
import java.math.BigDecimal;
import java.time.Instant;
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
class AdminEmployeeRoleApiIntegrationTests {

    private static final String ADMIN_EMAIL = "a2-admin@restosphere.test";
    private static final String WAITER_EMAIL = "a2-waiter@restosphere.test";
    private static final String PASSWORD = "correct-horse-battery";
    private static final String USERS = "/api/v1/admin/employees";

    @Autowired MockMvc mockMvc;
    @Autowired EmployeeRepository users;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired AuditLogRepository auditLogs;
    @Autowired TableSessionRepository tableSessions;
    @Autowired CashierShiftRepository cashierShifts;
    @Autowired RestaurantTableRepository tables;
    @Autowired AreaRepository areas;
    @Autowired BillRepository bills;
    @Autowired PasswordEncoder passwordEncoder;

    Employee admin;
    Employee waiter;
    Area createdArea;
    RestaurantTable createdTable;

    @BeforeEach
    void setUp() {
        clean();
        admin = saveUser(ADMIN_EMAIL, Role.ADMIN);
        waiter = saveUser(WAITER_EMAIL, Role.WAITER);
    }

    @AfterEach
    void clean() {
        // Bang con tham chieu users phai xoa truoc.
        tableSessions.deleteAll();
        bills.deleteAll();
        cashierShifts.deleteAll();
        auditLogs.deleteAll();
        refreshTokens.deleteAll();
        users.deleteAll();
        if (createdTable != null) {
            tables.delete(createdTable);
            areas.delete(createdArea);
            createdTable = null;
        }
    }

    @Test
    void adminChangesRoleAndAccessFollowsDatabase() throws Exception {
        String oldWaiterToken = bearer(WAITER_EMAIL);

        setRole(bearer(ADMIN_EMAIL), waiter.getId(), "KITCHEN")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(waiter.getId().toString()))
                .andExpect(jsonPath("$.role").value("KITCHEN"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        assertThat(auditLogs.findAll())
                .singleElement()
                .satisfies(
                        a -> {
                            assertThat(a.getAction()).isEqualTo("ROLE_CHANGED");
                            assertThat(a.getTargetType()).isEqualTo("USER");
                            assertThat(a.getTargetId()).isEqualTo(waiter.getId());
                            assertThat(a.getDescription())
                                    .isEqualTo(WAITER_EMAIL + ": WAITER → KITCHEN");
                        });

        mockMvc.perform(
                        get("/api/v1/workspaces/kitchen")
                                .header(HttpHeaders.AUTHORIZATION, bearer(WAITER_EMAIL)))
                .andExpect(status().isOk());
        // Token cu van ghi WAITER nhung quyen doc theo DB.
        mockMvc.perform(
                        get("/api/v1/workspaces/staff")
                                .header(HttpHeaders.AUTHORIZATION, oldWaiterToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void sameRoleIsNoOpWithoutAudit() throws Exception {
        setRole(bearer(ADMIN_EMAIL), waiter.getId(), "WAITER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("WAITER"));
        assertThat(auditLogs.count()).isZero();
    }

    @Test
    void adminCannotChangeSelfButCanDemoteAnotherAdmin() throws Exception {
        String adminToken = bearer(ADMIN_EMAIL);
        setRole(adminToken, admin.getId(), "MANAGER")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Cannot change your own access"));

        Employee otherAdmin = saveUser("a2-admin2@restosphere.test", Role.ADMIN);
        setRole(adminToken, otherAdmin.getId(), "MANAGER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MANAGER"));
    }

    @Test
    void invalidRoleBodies() throws Exception {
        String adminToken = bearer(ADMIN_EMAIL);
        setRole(adminToken, waiter.getId(), "CUSTOMER").andExpect(status().isBadRequest());
        setRole(adminToken, waiter.getId(), "ABC").andExpect(status().isBadRequest());
        mockMvc.perform(
                        patch(USERS + "/" + waiter.getId() + "/role")
                                .header(HttpHeaders.AUTHORIZATION, adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void waiterWithOpenSessionIsBlocked() throws Exception {
        Bill bill = new Bill();
        bill.setCode("A2-" + UUID.randomUUID().toString().substring(0, 8));
        bills.save(bill);
        TableSession s = new TableSession();
        s.setBill(bill);
        s.setTable(anyTable());
        s.setWaiter(waiter);
        s.setGuestCount(2);
        s.setStartedAt(Instant.now());
        tableSessions.save(s);

        setRole(bearer(ADMIN_EMAIL), waiter.getId(), "KITCHEN")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Account has active work"));
        assertThat(users.findById(waiter.getId()).orElseThrow().getRole()).isEqualTo(Role.WAITER);
    }

    @Test
    void cashierWithOpenShiftIsBlocked() throws Exception {
        Employee cashier = saveUser("a2-cashier@restosphere.test", Role.CASHIER);
        CashierShift shift = new CashierShift();
        shift.setCashier(cashier);
        shift.setOpenedAt(Instant.now());
        shift.setOpeningCash(BigDecimal.ZERO);
        cashierShifts.save(shift);

        setRole(bearer(ADMIN_EMAIL), cashier.getId(), "WAITER")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Account has active work"));
    }

    @Test
    void accessControlAndUnknownUser() throws Exception {
        setRole(bearer(WAITER_EMAIL), waiter.getId(), "KITCHEN").andExpect(status().isForbidden());
        mockMvc.perform(
                        patch(USERS + "/" + waiter.getId() + "/role")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"role\":\"KITCHEN\"}"))
                .andExpect(status().isUnauthorized());
        setRole(bearer(ADMIN_EMAIL), UUID.randomUUID(), "KITCHEN").andExpect(status().isNotFound());
    }

    // Dung ban seed neu con; test catalog co the da xoa het ban nen tu tao khi can.
    private RestaurantTable anyTable() {
        return tables.findAll().stream()
                .findFirst()
                .orElseGet(
                        () -> {
                            createdArea = new Area();
                            createdArea.setName("a2-area");
                            areas.save(createdArea);
                            createdTable = new RestaurantTable();
                            createdTable.setArea(createdArea);
                            createdTable.setCode("A2-T1");
                            createdTable.setCapacity(4);
                            return tables.save(createdTable);
                        });
    }

    private ResultActions setRole(String token, UUID id, String role) throws Exception {
        return mockMvc.perform(
                patch(USERS + "/" + id + "/role")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"%s\"}".formatted(role)));
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
