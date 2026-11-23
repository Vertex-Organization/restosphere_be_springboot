package com.vertex.restosphere;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.entity.Area;
import com.vertex.restosphere.entity.Employee;
import com.vertex.restosphere.entity.Reservation;
import com.vertex.restosphere.repository.EmployeeRepository;
import com.vertex.restosphere.service.WorkspaceStore;
import com.vertex.restosphere.service.impl.ReservationMailer;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CustomerApiIntegrationTests {
    static final String REGISTER = "/api/v1/customers/register";
    static final String MANAGE = "/api/v1/manager/customers";

    @Autowired MockMvc mvc;
    @Autowired EmployeeRepository users;
    @Autowired WorkspaceStore db;
    @MockitoSpyBean ReservationMailer mailer;

    RequestPostProcessor as(Role role) {
        var u = new Employee();
        u.setEmail(UUID.randomUUID() + "@test.example");
        u.setFullName(role.name());
        u.setPasswordHash("unused");
        u.setRole(role);
        users.save(u);
        return jwt().jwt(j -> j.subject(u.getId().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    static String body(String name, String phone) {
        return """
                {"fullName":"%s","phone":"%s","email":"an@example.com",
                 "birthday":"1995-04-01","marketingConsent":true,"note":"VIP"}
                """
                .formatted(name, phone);
    }

    String register(String name, String phone) throws Exception {
        var res =
                mvc.perform(
                                post(REGISTER)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body(name, phone)))
                        .andExpect(status().isCreated())
                        .andReturn();
        return JsonPath.read(res.getResponse().getContentAsString(), "$.id");
    }

    @Test
    void visitorRegistersWithoutLoginAndCannotSetStaffNote() throws Exception {
        mvc.perform(
                        post(REGISTER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("Nguyễn An", "+84 901 234 567")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phone").value("0901234567"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.note").doesNotExist());
        org.mockito.Mockito.verify(mailer)
                .sendWelcome(
                        org.mockito.ArgumentMatchers.argThat(
                                c -> c.getPhone().equals("0901234567")));
    }

    @Test
    void responseReportsWhenTheWelcomeEmailWasQueued() throws Exception {
        org.mockito.Mockito.doReturn(true)
                .when(mailer)
                .sendWelcome(org.mockito.ArgumentMatchers.any());
        mvc.perform(
                        post(REGISTER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("Phạm Lan", "0909000001")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.welcomeEmailSent").value(true));
        org.mockito.Mockito.doReturn(false)
                .when(mailer)
                .sendWelcome(org.mockito.ArgumentMatchers.any());
        mvc.perform(
                        post(REGISTER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("Phạm Hà", "0909000002")))
                .andExpect(jsonPath("$.welcomeEmailSent").value(false));
        mvc.perform(get(MANAGE).with(as(Role.MANAGER)).param("q", "0909000001"))
                .andExpect(jsonPath("$.items[0].welcomeEmailSent").doesNotExist());
    }

    @Test
    void welcomeEmailIsSkippedWithoutAddressOrWhenNotificationsAreOff() {
        var c = new com.vertex.restosphere.entity.Customer();
        c.setFullName("Lan");
        c.setPhone("0909000003");
        org.junit.jupiter.api.Assertions.assertFalse(mailer.sendWelcome(c), "no email address");
        c.setEmail("lan@example.com");
        db.lock().setEmailNotifications(false);
        org.junit.jupiter.api.Assertions.assertFalse(mailer.sendWelcome(c), "notifications off");
    }

    @Test
    void duplicatePhoneIsRejectedInEitherFormat() throws Exception {
        register("An", "0901234568");
        mvc.perform(
                        post(REGISTER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("Bình", "+84901234568")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.field").value("phone"))
                .andExpect(jsonPath("$.detail").value("Số điện thoại này đã là thành viên."));
    }

    @Test
    void invalidInputReturnsFieldErrors() throws Exception {
        mvc.perform(
                        post(REGISTER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"fullName\":\"\",\"phone\":\"abc\",\"email\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.fullName").value("Vui lòng nhập họ tên."))
                .andExpect(jsonPath("$.errors.phone").value("Số điện thoại không hợp lệ."))
                .andExpect(jsonPath("$.errors.email").value("Email không hợp lệ."));
    }

    @Test
    void onlyManagersAndAdminsCanManageMembers() throws Exception {
        mvc.perform(get(MANAGE)).andExpect(status().isUnauthorized());
        mvc.perform(get(MANAGE).with(as(Role.RECEPTIONIST))).andExpect(status().isForbidden());
        mvc.perform(get(MANAGE).with(as(Role.ADMIN))).andExpect(status().isOk());
    }

    @Test
    void managerSearchesAndSeesReservationHistoryMatchedByPhone() throws Exception {
        String id = register("Trần Mai", "0907000001");
        var area = new Area();
        area.setName("Khu thành viên " + UUID.randomUUID());
        db.save(area);
        reservation(area, "0907000001", Reservation.Status.COMPLETED, Instant.now());
        reservation(area, "+84907000001", Reservation.Status.CANCELLED, null);
        reservation(area, "0907000999", Reservation.Status.COMPLETED, Instant.now());
        var manager = as(Role.MANAGER);

        mvc.perform(get(MANAGE).param("q", "+84 907 000 001").with(manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(id))
                .andExpect(jsonPath("$.items[0].reservationCount").value(2))
                .andExpect(jsonPath("$.items[0].lastVisitAt").isNotEmpty());
        mvc.perform(get(MANAGE).param("q", "trần").with(manager))
                .andExpect(jsonPath("$.items[0].id").value(id));
        mvc.perform(get(MANAGE + "/" + id).with(manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recentReservations.length()").value(2));
        mvc.perform(get(MANAGE).param("size", "0").with(manager))
                .andExpect(status().isBadRequest());
    }

    @Test
    void managerCreatesUpdatesAndLocksMembers() throws Exception {
        var manager = as(Role.MANAGER);
        var res =
                mvc.perform(
                                post(MANAGE)
                                        .with(manager)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body("Lê Hoa", "0908000001")))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.note").value("VIP"))
                        .andReturn();
        String id = JsonPath.read(res.getResponse().getContentAsString(), "$.id");
        String other = register("Khác", "0908000002");

        mvc.perform(
                        put(MANAGE + "/" + id)
                                .with(manager)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("Lê Thu Hoa", "0908000001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Lê Thu Hoa"));
        mvc.perform(
                        put(MANAGE + "/" + other)
                                .with(manager)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("Khác", "0908000001")))
                .andExpect(status().isConflict());
        mvc.perform(
                        patch(MANAGE + "/" + id + "/status")
                                .with(manager)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"LOCKED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOCKED"));
        mvc.perform(get(MANAGE).param("status", "LOCKED").with(manager))
                .andExpect(jsonPath("$.items[?(@.id=='" + id + "')]").isNotEmpty());
        mvc.perform(get(MANAGE + "/" + UUID.randomUUID()).with(manager))
                .andExpect(status().isNotFound());
    }

    void reservation(Area area, String phone, Reservation.Status status, Instant arrivedAt) {
        var r = new Reservation();
        r.setCode("RS-T-" + UUID.randomUUID().toString().substring(0, 8));
        r.setCustomerName("Khách");
        r.setPhone(phone);
        r.setArea(area);
        r.setGuestCount(2);
        r.setStartsAt(Instant.now().minusSeconds(7200));
        r.setEndsAt(Instant.now().minusSeconds(3600));
        r.setSource(Reservation.Source.WEB);
        r.setStatus(status);
        r.setArrivedAt(arrivedAt);
        db.save(r);
    }
}
