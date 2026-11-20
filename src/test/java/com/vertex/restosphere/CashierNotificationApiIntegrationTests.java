package com.vertex.restosphere;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.entity.Notification;
import com.vertex.restosphere.entity.User;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class CashierNotificationApiIntegrationTests extends CashierApiTestBase {
    @Override
    String prefix() {
        return "c9";
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

    private Map<String, Object> find(List<Map<String, Object>> list, Notification n) {
        return list.stream().filter(x -> x.get("id").equals(n.getId().toString())).findFirst().orElseThrow();
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
    void listShowsCashierAudienceAndOwnRecipientWithBillAndRequestIds() throws Exception {
        Notification bill = notification("PAYMENT_REQUEST", Role.CASHIER, null, "bill-1");
        Notification decided = notification("REQUEST_DECIDED", Role.CASHIER, null, "request-1");
        Notification personal = notification("TRANSFER_RECEIVED", null, cashier, "bill-2");
        Notification waiterOnly = notification("PAYMENT", Role.WAITER, null, "session-1");
        User other = saveUser("c9-cashier2@restosphere.test", Role.CASHIER);
        Notification forOther = notification("TRANSFER_FAILED", null, other, "bill-3");

        List<Map<String, Object>> list = json(ok(call(HttpMethod.GET, "/notifications", cashierToken, null)), "$");
        List<String> ids = list.stream().map(m -> (String) m.get("id")).toList();
        assertThat(ids).contains(bill.getId().toString(), decided.getId().toString(), personal.getId().toString())
                .doesNotContain(waiterOnly.getId().toString(), forOther.getId().toString());
        assertThat(find(list, bill).get("billId")).isEqualTo("bill-1");
        assertThat(find(list, bill).get("requestId")).isNull();
        assertThat(find(list, decided).get("requestId")).isEqualTo("request-1");
        assertThat(find(list, decided).get("billId")).isNull();
        assertThat((List<?>) find(list, bill).get("readBy")).isEmpty();
    }

    @Test
    void readMarksOnlyRequestedIdsAndIsIdempotent() throws Exception {
        Notification a = notification("PAYMENT_REQUEST", Role.CASHIER, null, "a");
        Notification b = notification("PAYMENT_REQUEST", Role.CASHIER, null, "b");
        long before = revision();

        List<Map<String, Object>> list = json(ok(call(HttpMethod.POST, "/notifications/read", cashierToken,
                "{\"ids\":[\"%s\",\"not-a-uuid\"]}".formatted(a.getId()))), "$");
        assertThat((List<Object>) find(list, a).get("readBy")).containsExactly(cashier.getId().toString());
        assertThat((List<?>) find(list, b).get("readBy")).isEmpty();
        assertThat(readsBy(cashier)).isEqualTo(1);
        assertThat(revision()).isEqualTo(before + 1);
        assertThat(auditCount("read-notifications")).isEqualTo(1);

        ok(call(HttpMethod.POST, "/notifications/read", cashierToken, "{\"ids\":[\"%s\"]}".formatted(a.getId())));
        assertThat(readsBy(cashier)).isEqualTo(1);
    }

    @Test
    void readWithoutIdsMarksEverythingVisible() throws Exception {
        notification("PAYMENT_REQUEST", Role.CASHIER, null, "a");
        notification("TRANSFER_RECEIVED", null, cashier, "b");
        notification("PAYMENT", Role.WAITER, null, "c");

        ok(call(HttpMethod.POST, "/notifications/read", cashierToken, null));
        assertThat(readsBy(cashier)).isEqualTo(2);
        ok(call(HttpMethod.POST, "/notifications/read", cashierToken, "{\"ids\":[]}"));
        assertThat(readsBy(cashier)).isEqualTo(2);
    }

    @Test
    void meReturnsProfilePermissionsAndDiscountLimit() throws Exception {
        String body = ok(call(HttpMethod.GET, "/me", cashierToken, null));
        assertThat(this.<String>json(body, "$.id")).isEqualTo(cashier.getId().toString());
        assertThat(this.<String>json(body, "$.username")).isEqualTo(cashier.getEmail());
        assertThat(this.<String>json(body, "$.role")).isEqualTo("CASHIER");
        assertThat(this.<Integer>json(body, "$.discountLimitPercent")).isEqualTo(10);
        assertThat(this.<List<String>>json(body, "$.permissions")).contains("bills.view", "payments.create",
                "shifts.manage");
        assertThat(this.<String>json(body, "$.shift")).isEmpty();
    }
}
