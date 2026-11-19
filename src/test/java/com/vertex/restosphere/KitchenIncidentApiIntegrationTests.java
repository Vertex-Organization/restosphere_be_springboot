package com.vertex.restosphere;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.entity.Incident;
import com.vertex.restosphere.entity.Order;
import com.vertex.restosphere.entity.OrderItem;
import com.vertex.restosphere.entity.User;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class KitchenIncidentApiIntegrationTests extends KitchenApiTestBase {

    @Override
    String prefix() {
        return "k4";
    }

    private String body(String type, String severity, String description) {
        return "{\"type\":\"" + type + "\",\"severity\":\"" + severity + "\",\"description\":\"" + description
                + "\"}";
    }

    private long incidentCount() {
        return count("select count(i) from Incident i where i.reporter.id = :p0", kitchen.getId());
    }

    @Test
    void authIsEnforced() throws Exception {
        call(HttpMethod.GET, "/incidents", null, null).andExpect(status().isUnauthorized());
        call(HttpMethod.GET, "/incidents", waiterToken, null).andExpect(status().isForbidden());
        call(HttpMethod.POST, "/incidents", waiterToken, body("OTHER", "LOW", "x")).andExpect(status().isForbidden());
        assertThat(incidentCount()).isZero();
    }

    @Test
    void reportCreatesOpenIncidentNotifiesManagerBumpsRevisionAndAudits() throws Exception {
        User manager = saveUser("k4-manager@restosphere.test", Role.MANAGER);
        long rev = revision();

        String res = created(call(HttpMethod.POST, "/incidents", kitchenToken,
                body("EQUIPMENT", "HIGH", "  Lo nuong hong  ")));
        String id = json(res, "$.id");
        assertThat((String) json(res, "$.code")).isEqualTo("I-" + id);
        assertThat((String) json(res, "$.type")).isEqualTo("EQUIPMENT");
        assertThat((String) json(res, "$.title")).isEqualTo("EQUIPMENT");
        assertThat((String) json(res, "$.severity")).isEqualTo("HIGH");
        assertThat((String) json(res, "$.status")).isEqualTo("OPEN");
        assertThat((String) json(res, "$.description")).isEqualTo("Lo nuong hong");
        assertThat((String) json(res, "$.reporterId")).isEqualTo(kitchen.getId().toString());
        assertThat((Object) json(res, "$.at")).isNotNull();
        assertThat((List<String>) json(res, "$.notifiedStaffIds")).containsExactly(manager.getId().toString());

        assertThat(revision()).isEqualTo(rev + 1);
        assertThat(auditCount("report-incident")).isEqualTo(1);
        assertThat(count("select count(n) from Notification n where n.kind = 'INCIDENT' "
                + "and n.audienceRole = com.vertex.restosphere.common.enums.Role.MANAGER and n.path = :p0", id))
                .isEqualTo(1);
    }

    @Test
    void reportWithItemLinksOrderAndChecksMismatch() throws Exception {
        Fixture f = fixture();
        Order o1 = order(f.session(), 1);
        Order o2 = order(f.session(), 2);
        OrderItem item = item(o1, f.menu());

        String ok = created(call(HttpMethod.POST, "/incidents", kitchenToken,
                "{\"type\":\"CANNOT_PREPARE\",\"severity\":\"MEDIUM\",\"description\":\"Het bot\",\"orderId\":\""
                        + o1.getId() + "\",\"itemId\":\"" + item.getId() + "\"}"));
        assertThat((String) json(ok, "$.itemId")).isEqualTo(item.getId().toString());
        assertThat((String) json(ok, "$.orderId")).isEqualTo(o1.getId().toString());

        // Chi co itemId: orderId suy ra tu mon. Chi co orderId (khong itemId): bi bo qua nhu lenh cu.
        String itemOnly = created(call(HttpMethod.POST, "/incidents", kitchenToken,
                "{\"type\":\"OTHER\",\"severity\":\"LOW\",\"description\":\"a\",\"itemId\":\"" + item.getId()
                        + "\"}"));
        assertThat((String) json(itemOnly, "$.orderId")).isEqualTo(o1.getId().toString());
        String orderOnly = created(call(HttpMethod.POST, "/incidents", kitchenToken,
                "{\"type\":\"OTHER\",\"severity\":\"LOW\",\"description\":\"b\",\"orderId\":\"" + o2.getId()
                        + "\"}"));
        assertThat((Object) json(orderOnly, "$.orderId")).isNull();

        long before = incidentCount();
        long rev = revision();
        call(HttpMethod.POST, "/incidents", kitchenToken,
                "{\"type\":\"OTHER\",\"severity\":\"LOW\",\"description\":\"c\",\"orderId\":\"" + o2.getId()
                        + "\",\"itemId\":\"" + item.getId() + "\"}").andExpect(status().isBadRequest());
        call(HttpMethod.POST, "/incidents", kitchenToken,
                "{\"type\":\"OTHER\",\"severity\":\"LOW\",\"description\":\"c\",\"itemId\":\"nope\"}")
                .andExpect(status().isBadRequest());
        call(HttpMethod.POST, "/incidents", kitchenToken,
                "{\"type\":\"OTHER\",\"severity\":\"LOW\",\"description\":\"c\",\"itemId\":\"" + UUID.randomUUID()
                        + "\"}").andExpect(status().isNotFound());
        assertThat(incidentCount()).isEqualTo(before);
        assertThat(revision()).isEqualTo(rev);
    }

    @Test
    void validationErrorsAre400AndLeaveNothingBehind() throws Exception {
        long rev = revision();
        call(HttpMethod.POST, "/incidents", kitchenToken, body("NOPE", "LOW", "x")).andExpect(status().isBadRequest());
        call(HttpMethod.POST, "/incidents", kitchenToken, body("OTHER", "SEVERE", "x"))
                .andExpect(status().isBadRequest());
        call(HttpMethod.POST, "/incidents", kitchenToken, body("OTHER", "LOW", "   "))
                .andExpect(status().isBadRequest());
        call(HttpMethod.POST, "/incidents", kitchenToken, body("OTHER", "LOW", "x".repeat(2001)))
                .andExpect(status().isBadRequest());
        call(HttpMethod.POST, "/incidents", kitchenToken, "{\"severity\":\"LOW\",\"description\":\"x\"}")
                .andExpect(status().isBadRequest());
        call(HttpMethod.POST, "/incidents", kitchenToken, "{\"type\":\"OTHER\",\"description\":\"x\"}")
                .andExpect(status().isBadRequest());
        assertThat(incidentCount()).isZero();
        assertThat(revision()).isEqualTo(rev);
        assertThat(auditCount("report-incident")).isZero();

        // CRITICAL va do dai toi da van hop le nhu lenh cu.
        created(call(HttpMethod.POST, "/incidents", kitchenToken, body("OUT_OF_STOCK", "CRITICAL", "x".repeat(2000))));
        assertThat(incidentCount()).isEqualTo(1);
    }

    @Test
    void listShowsAllIncidentsIncludingResolution() throws Exception {
        User other = saveUser("k4-kitchen2@restosphere.test", Role.KITCHEN);
        User manager = saveUser("k4-manager@restosphere.test", Role.MANAGER);
        Incident mine = new Incident();
        mine.setType("OTHER");
        mine.setTitle("OTHER");
        mine.setDescription("mine");
        mine.setSeverity(Incident.Severity.LOW);
        mine.setReporter(kitchen);
        mine = persist(mine);
        Incident theirs = new Incident();
        theirs.setType("EQUIPMENT");
        theirs.setTitle("EQUIPMENT");
        theirs.setDescription("theirs");
        theirs.setSeverity(Incident.Severity.HIGH);
        theirs.setReporter(other);
        theirs.setAssignee(manager);
        theirs.setStatus(Incident.Status.RESOLVED);
        theirs.setResolution("Da sua");
        theirs = persist(theirs);

        String res = ok(call(HttpMethod.GET, "/incidents", kitchenToken, null));
        List<Map<String, Object>> list = json(res, "$");
        assertThat(list).hasSize(2);
        assertThat(list.get(0)).containsEntry("id", mine.getId().toString()).containsEntry("status", "OPEN");
        assertThat(list.get(0).get("response")).isNull();
        Map<String, Object> second = list.get(1);
        assertThat(second).containsEntry("id", theirs.getId().toString()).containsEntry("status", "RESOLVED")
                .containsEntry("reporterId", other.getId().toString())
                .containsEntry("assigneeId", manager.getId().toString());
        assertThat((Map<String, Object>) second.get("response")).containsEntry("text", "Da sua")
                .containsEntry("by", manager.getId().toString());
        assertThat((List<String>) second.get("notifiedStaffIds")).containsExactly(manager.getId().toString());
    }
}
