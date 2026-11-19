package com.vertex.restosphere;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.entity.Notification;
import com.vertex.restosphere.entity.NotificationRead;
import com.vertex.restosphere.entity.Shift;
import com.vertex.restosphere.entity.ShiftAssignment;
import com.vertex.restosphere.entity.User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class KitchenNotificationApiIntegrationTests extends KitchenApiTestBase {

    @Override
    String prefix() {
        return "k5";
    }

    private Notification notification(String kind, Role audience, User recipient, String path) {
        Notification n = new Notification();
        n.setKind(kind);
        n.setTitle("t-" + kind);
        n.setBody("b-" + kind);
        n.setAudienceRole(audience);
        n.setRecipient(recipient);
        n.setPath(path);
        return persist(n);
    }

    private long readsBy(User u) {
        return count("select count(r) from NotificationRead r where r.user.id = :p0", u.getId());
    }

    private boolean readByMe(List<Map<String, Object>> list, Notification n) {
        Map<String, Object> m = list.stream().filter(x -> x.get("id").equals(n.getId().toString())).findFirst()
                .orElseThrow();
        return ((List<?>) m.get("readBy")).contains(kitchen.getId().toString());
    }

    @Test
    void authIsEnforced() throws Exception {
        for (String p : List.of("/notifications", "/me")) {
            call(HttpMethod.GET, p, null, null).andExpect(status().isUnauthorized());
            call(HttpMethod.GET, p, waiterToken, null).andExpect(status().isForbidden());
        }
        call(HttpMethod.POST, "/notifications/read", waiterToken, "{}").andExpect(status().isForbidden());
        call(HttpMethod.POST, "/notifications/read", null, "{}").andExpect(status().isUnauthorized());
    }

    @Test
    void listShowsOnlyKitchenAudienceAndOwnRecipientWithOrderIdAndReadBy() throws Exception {
        User other = saveUser("k5-kitchen2@restosphere.test", Role.KITCHEN);
        String orderId = UUID.randomUUID().toString();
        Notification newOrder = notification("NEW_ORDER", Role.KITCHEN, null, orderId);
        Notification menu = notification("MENU_REQUEST", Role.KITCHEN, null, "x");
        Notification direct = notification("INCIDENT_REPLY", null, kitchen, "y");
        notification("READY", Role.WAITER, null, "z");
        notification("INCIDENT_REPLY", null, other, "w");
        NotificationRead r = new NotificationRead();
        r.setNotification(newOrder);
        r.setUser(other);
        r.setReadAt(Instant.now());
        persist(r);

        String body = ok(call(HttpMethod.GET, "/notifications", kitchenToken, null));
        List<Map<String, Object>> list = json(body, "$");
        assertThat(list).extracting(m -> m.get("id")).containsExactlyInAnyOrder(newOrder.getId().toString(),
                menu.getId().toString(), direct.getId().toString());
        Map<String, Object> no = list.stream().filter(m -> m.get("id").equals(newOrder.getId().toString()))
                .findFirst().orElseThrow();
        assertThat(no).containsEntry("kind", "NEW_ORDER").containsEntry("title", "t-NEW_ORDER")
                .containsEntry("body", "b-NEW_ORDER").containsEntry("orderId", orderId)
                .containsEntry("path", orderId);
        assertThat((List<Object>) no.get("readBy")).containsExactly(other.getId().toString());
        assertThat(no.get("at")).isNotNull();
        Map<String, Object> mr = list.stream().filter(m -> m.get("id").equals(menu.getId().toString()))
                .findFirst().orElseThrow();
        assertThat(mr.get("orderId")).isNull();
        assertThat((List<?>) mr.get("readBy")).isEmpty();
    }

    @Test
    void readSelectedMarksOnlyThoseVisibleToCallerAndIsIdempotent() throws Exception {
        Notification a = notification("NEW_ORDER", Role.KITCHEN, null, UUID.randomUUID().toString());
        Notification b = notification("PRIORITY", Role.KITCHEN, null, UUID.randomUUID().toString());
        Notification waiterOnly = notification("READY", Role.WAITER, null, "z");
        long rev = revision();

        String res = ok(call(HttpMethod.POST, "/notifications/read", kitchenToken,
                "{\"ids\":[\"" + a.getId() + "\",\"" + waiterOnly.getId() + "\",\"garbage\"]}"));
        List<Map<String, Object>> list = json(res, "$");
        assertThat(list).hasSize(2);
        assertThat(readByMe(list, a)).isTrue();
        assertThat(readByMe(list, b)).isFalse();
        assertThat(readsBy(kitchen)).isEqualTo(1);
        assertThat(revision()).isEqualTo(rev + 1);
        assertThat(auditCount("read-notifications")).isEqualTo(1);

        // Doc lai khong ghi trung (unique notification+user) va khong loi.
        ok(call(HttpMethod.POST, "/notifications/read", kitchenToken, "{\"ids\":[\"" + a.getId() + "\"]}"));
        assertThat(readsBy(kitchen)).isEqualTo(1);
        // Chi co id rac: khong doc gi (khong bi hieu nham thanh doc het).
        ok(call(HttpMethod.POST, "/notifications/read", kitchenToken, "{\"ids\":[\"garbage\"]}"));
        assertThat(readsBy(kitchen)).isEqualTo(1);
        assertThat(readsBy(waiter)).isZero();
    }

    @Test
    void readWithoutIdsReadsEverythingVisible() throws Exception {
        Notification a = notification("NEW_ORDER", Role.KITCHEN, null, UUID.randomUUID().toString());
        Notification b = notification("INCIDENT_REPLY", null, kitchen, "y");
        notification("READY", Role.WAITER, null, "z");

        for (String payload : new String[] {null, "{}", "{\"ids\":[]}"}) {
            tx.executeWithoutResult(s -> em.createQuery("delete from NotificationRead r where r.user.id = :u")
                    .setParameter("u", kitchen.getId()).executeUpdate());
            String res = ok(call(HttpMethod.POST, "/notifications/read", kitchenToken, payload));
            List<Map<String, Object>> list = json(res, "$");
            assertThat(readByMe(list, a)).isTrue();
            assertThat(readByMe(list, b)).isTrue();
            assertThat(readsBy(kitchen)).isEqualTo(2);
        }
        assertThat(readsBy(waiter)).isZero();
    }

    @Test
    void meReturnsProfilePermissionsAndCurrentShift() throws Exception {
        Instant now = Instant.now();
        assignShift("K5 Past", now.minus(10, ChronoUnit.HOURS), now.minus(2, ChronoUnit.HOURS));
        assignShift("K5 Future", now.plus(2, ChronoUnit.HOURS), now.plus(10, ChronoUnit.HOURS));

        String none = ok(call(HttpMethod.GET, "/me", kitchenToken, null));
        assertThat((String) json(none, "$.shift")).isEmpty();
        assertThat((String) json(none, "$.id")).isEqualTo(kitchen.getId().toString());
        assertThat((String) json(none, "$.name")).isEqualTo(kitchen.getFullName());
        assertThat((String) json(none, "$.username")).isEqualTo(kitchen.getEmail());
        assertThat((String) json(none, "$.role")).isEqualTo("KITCHEN");
        assertThat((String) json(none, "$.position")).isEqualTo("KITCHEN");
        assertThat((List<String>) json(none, "$.permissions")).containsExactly("kitchen.items.update",
                "kitchen.incidents.create", "kitchen.menu.availability");

        assignShift("K5 Now", now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS));
        String cur = ok(call(HttpMethod.GET, "/me", kitchenToken, null));
        assertThat((String) json(cur, "$.shift")).isEqualTo("K5 Now");
    }

    private void assignShift(String name, Instant start, Instant end) {
        Shift s = new Shift();
        s.setName(name);
        s.setStartsAt(start);
        s.setEndsAt(end);
        s = persist(s);
        ShiftAssignment a = new ShiftAssignment();
        a.setShift(s);
        a.setUser(kitchen);
        persist(a);
    }
}
