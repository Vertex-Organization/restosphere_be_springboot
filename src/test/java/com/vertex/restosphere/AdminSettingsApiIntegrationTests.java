package com.vertex.restosphere;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.entity.Area;
import com.vertex.restosphere.entity.Bill;
import com.vertex.restosphere.entity.RestaurantSettings;
import com.vertex.restosphere.entity.RestaurantTable;
import com.vertex.restosphere.entity.TableSession;
import com.vertex.restosphere.entity.User;
import com.vertex.restosphere.repository.AreaRepository;
import com.vertex.restosphere.repository.AuditLogRepository;
import com.vertex.restosphere.repository.BillRepository;
import com.vertex.restosphere.repository.RefreshTokenRepository;
import com.vertex.restosphere.repository.RestaurantSettingsRepository;
import com.vertex.restosphere.repository.RestaurantTableRepository;
import com.vertex.restosphere.repository.TableSessionRepository;
import com.vertex.restosphere.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
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
import tools.jackson.databind.json.JsonMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AdminSettingsApiIntegrationTests {

    private static final String ADMIN_EMAIL = "a4-admin@restosphere.test";
    private static final String PASSWORD = "correct-horse-battery";
    private static final String URL = "/api/v1/admin/settings";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository users;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired AuditLogRepository auditLogs;
    @Autowired RestaurantSettingsRepository settingsRepo;
    @Autowired BillRepository bills;
    @Autowired TableSessionRepository tableSessions;
    @Autowired RestaurantTableRepository tables;
    @Autowired AreaRepository areas;
    @Autowired PasswordEncoder passwordEncoder;

    User admin;
    RestaurantSettings original;
    Bill createdBill;
    TableSession createdSession;
    Area createdArea;
    RestaurantTable createdTable;

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
        s.setOnlineReservations(original.isOnlineReservations());
        s.setHoldMinutes(original.getHoldMinutes());
        s.setNoShowMinutes(original.getNoShowMinutes());
        s.setBufferMinutes(original.getBufferMinutes());
        s.setServiceChargeRate(original.getServiceChargeRate());
        s.setVatRate(original.getVatRate());
        s.setBankAccountNumber(original.getBankAccountNumber());
        s.setBankName(original.getBankName());
        s.setBankAccountName(original.getBankAccountName());
        s.setTimezone(original.getTimezone());
        s.setCurrency(original.getCurrency());
        s.setEmailNotifications(original.isEmailNotifications());
        settingsRepo.save(s);
        // Hoa don -> phien -> ban/khu tu tao, theo thu tu khoa ngoai.
        if (createdBill != null) {
            bills.delete(createdBill);
            tableSessions.delete(createdSession);
            createdBill = null;
        }
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
    void adminGetsSettingsShape() throws Exception {
        String body =
                mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_EMAIL)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.billing.bankName").isString())
                        .andExpect(jsonPath("$.billing.bankAccountName").isString())
                        .andExpect(jsonPath("$.billing.bankAccount").isString())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        Map<String, Object> json = JsonPath.read(body, "$");
        assertThat(json)
                .containsOnlyKeys(
                        "onlineReservations",
                        "tableHoldMinutes",
                        "noShowMinutes",
                        "bufferMinutes",
                        "billing",
                        "timezone",
                        "currency",
                        "emailNotifications");
        Map<String, Object> billing = JsonPath.read(body, "$.billing");
        assertThat(billing)
                .containsOnlyKeys(
                        "serviceChargePercent",
                        "vatPercent",
                        "bankAccount",
                        "bankName",
                        "bankAccountName");
    }

    @Test
    void updateSavesChangesAndSameDataIsNoOp() throws Exception {
        String token = bearer(ADMIN_EMAIL);
        putSettings(token, settings()).andExpect(status().isOk());
        auditLogs.deleteAll();

        Map<String, Object> body = settings();
        body.put("bufferMinutes", 20);
        billing(body).put("vatPercent", 10);
        billing(body).put("bankName", "  ACB  ");
        putSettings(token, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billing.bankName").value("ACB"));
        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.bufferMinutes").value(20))
                .andExpect(jsonPath("$.billing.vatPercent").value(10.0))
                .andExpect(jsonPath("$.billing.bankName").value("ACB"));
        assertThat(auditLogs.findAll())
                .singleElement()
                .satisfies(
                        a -> {
                            assertThat(a.getAction()).isEqualTo("SETTINGS_UPDATED");
                            assertThat(a.getTargetType()).isEqualTo("SETTINGS");
                            assertThat(a.getTargetId()).isEqualTo(original.getId());
                            assertThat(a.getDescription())
                                    .isEqualTo(
                                            "Settings updated: bufferMinutes, billing.vatPercent,"
                                                    + " billing.bankName");
                        });

        // Gui lai cung du lieu (5 trong khi DB luu 5.00): khong ghi them log.
        putSettings(token, body).andExpect(status().isOk());
        assertThat(auditLogs.count()).isEqualTo(1);
    }

    @Test
    void invalidInputIsRejectedAndNothingChanges() throws Exception {
        String token = bearer(ADMIN_EMAIL);
        putSettings(token, settings()).andExpect(status().isOk());
        auditLogs.deleteAll();
        String before =
                mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, token))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        Object[][] cases = {
            {"noShowMinutes", 4},
            {"noShowMinutes", 121},
            {"bufferMinutes", 61},
            {"tableHoldMinutes", 4},
            {"billing.serviceChargePercent", 31},
            {"billing.vatPercent", 21},
            {"billing.vatPercent", new BigDecimal("8.555")},
            {"billing.bankAccount", "12ab"},
            {"timezone", "Mars/Phobos"},
            {"currency", "USD"},
            {"onlineReservations", null}
        };
        for (Object[] c : cases) {
            Map<String, Object> body = settings();
            String key = (String) c[0];
            Map<String, Object> target = key.startsWith("billing.") ? billing(body) : body;
            String field = key.replace("billing.", "");
            // Ca nao cung doi them bufferMinutes de chac chan request bi chan ca, khong luu nua
            // chung.
            body.put("bufferMinutes", 30);
            if (c[1] == null) {
                target.remove(field);
            } else {
                target.put(field, c[1]);
            }
            putSettings(token, body).andExpect(status().isBadRequest());
        }

        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(r -> assertThat(r.getResponse().getContentAsString()).isEqualTo(before));
        assertThat(auditLogs.count()).isZero();
    }

    @Test
    void openBillBlocksRateChangeOnly() throws Exception {
        String token = bearer(ADMIN_EMAIL);
        putSettings(token, settings()).andExpect(status().isOk());
        openBill();

        Map<String, Object> body = settings();
        billing(body).put("vatPercent", 10);
        body.put("bufferMinutes", 20);
        putSettings(token, body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Cannot change rates while bills are open"));
        RestaurantSettings s = settingsRepo.findBySingletonKey(1).orElseThrow();
        assertThat(s.getVatRate()).isEqualByComparingTo("8");
        assertThat(s.getBufferMinutes()).isEqualTo(15);

        billing(body).put("vatPercent", 8);
        putSettings(token, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bufferMinutes").value(20));
    }

    @Test
    void cashierWorkspaceSeesNewRatesAndBank() throws Exception {
        Map<String, Object> body = settings();
        billing(body).put("vatPercent", 10);
        billing(body).put("bankName", "ACB");
        putSettings(bearer(ADMIN_EMAIL), body).andExpect(status().isOk());

        // Cong thuc trong WorkspaceSnapshots: taxRate = vatRate.movePointRight(2); bank chi co khi
        // co so TK.
        BigDecimal taxRate =
                settingsRepo.findBySingletonKey(1).orElseThrow().getVatRate().movePointRight(2);
        saveUser("a4-cashier@restosphere.test", Role.CASHIER);
        String json =
                mockMvc.perform(
                                get("/api/v1/workspaces/cashier")
                                        .header(
                                                HttpHeaders.AUTHORIZATION,
                                                bearer("a4-cashier@restosphere.test")))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.config.bank.bankName").value("ACB"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(new BigDecimal(JsonPath.read(json, "$.config.taxRate").toString()))
                .isEqualByComparingTo(taxRate);
        assertThat(taxRate).isEqualByComparingTo("1000");
    }

    @Test
    void accessControl() throws Exception {
        saveUser("a4-manager@restosphere.test", Role.MANAGER);
        mockMvc.perform(
                        get(URL).header(
                                        HttpHeaders.AUTHORIZATION,
                                        bearer("a4-manager@restosphere.test")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());

        String adminToken = bearer(ADMIN_EMAIL);
        admin.setEnabled(false);
        users.save(admin);
        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isForbidden());
        putSettings(adminToken, settings()).andExpect(status().isForbidden());
    }

    /** Body hop le mau (giong hop dong API). */
    private Map<String, Object> settings() {
        Map<String, Object> billing = new LinkedHashMap<>();
        billing.put("serviceChargePercent", 5);
        billing.put("vatPercent", 8);
        billing.put("bankAccount", "0123456789");
        billing.put("bankName", "Vietcombank");
        billing.put("bankAccountName", "CONG TY RESTOSPHERE");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("onlineReservations", true);
        body.put("tableHoldMinutes", 15);
        body.put("noShowMinutes", 30);
        body.put("bufferMinutes", 15);
        body.put("billing", billing);
        body.put("timezone", "Asia/Ho_Chi_Minh");
        body.put("currency", "VND");
        body.put("emailNotifications", false);
        return body;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> billing(Map<String, Object> body) {
        return (Map<String, Object>) body.get("billing");
    }

    private void openBill() {
        createdSession = new TableSession();
        createdSession.setTable(anyTable());
        createdSession.setGuestCount(2);
        createdSession.setStartedAt(Instant.now());
        tableSessions.save(createdSession);
        createdBill = new Bill();
        createdBill.setCode("A4-" + UUID.randomUUID().toString().substring(0, 8));
        createdBill.setSession(createdSession);
        bills.save(createdBill);
    }

    // Dung ban seed neu con; test catalog co the da xoa het ban nen tu tao khi can.
    private RestaurantTable anyTable() {
        return tables.findAll().stream()
                .findFirst()
                .orElseGet(
                        () -> {
                            createdArea = new Area();
                            createdArea.setName("a4-area");
                            areas.save(createdArea);
                            createdTable = new RestaurantTable();
                            createdTable.setArea(createdArea);
                            createdTable.setCode("A4-T1");
                            createdTable.setCapacity(4);
                            return tables.save(createdTable);
                        });
    }

    private ResultActions putSettings(String token, Map<String, Object> body) throws Exception {
        return mockMvc.perform(
                put(URL).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(body)));
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
