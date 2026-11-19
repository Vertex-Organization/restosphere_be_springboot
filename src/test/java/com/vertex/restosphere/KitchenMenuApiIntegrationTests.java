package com.vertex.restosphere;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vertex.restosphere.common.enums.MenuItemStatus;
import com.vertex.restosphere.entity.ApprovalRequest;
import com.vertex.restosphere.entity.MenuItem;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class KitchenMenuApiIntegrationTests extends KitchenApiTestBase {

    @Override
    String prefix() {
        return "k3";
    }

    private MenuItem reload(MenuItem m) {
        return tx.execute(s -> em.find(MenuItem.class, m.getId()));
    }

    private String statusBody(String status) {
        return "{\"status\":\"" + status + "\"}";
    }

    private String requestBody(Object menuItemId, String status) {
        return "{\"menuItemId\":\"" + menuItemId + "\",\"status\":\"" + status + "\"}";
    }

    @Test
    void authIsEnforcedOnEveryEndpoint() throws Exception {
        MenuItem m = menu("A");
        String id = m.getId().toString();
        call(HttpMethod.GET, "/menu-items", null, null).andExpect(status().isUnauthorized());
        call(HttpMethod.GET, "/menu-items", waiterToken, null).andExpect(status().isForbidden());
        call(HttpMethod.PATCH, "/menu-items/" + id + "/status", waiterToken, statusBody("SOLD_OUT"))
                .andExpect(status().isForbidden());
        call(HttpMethod.GET, "/menu-requests", waiterToken, null).andExpect(status().isForbidden());
        call(HttpMethod.POST, "/menu-requests", waiterToken, requestBody(id, "SOLD_OUT"))
                .andExpect(status().isForbidden());
        assertThat(reload(m).getStatus()).isEqualTo(MenuItemStatus.AVAILABLE);
    }

    @Test
    void listMenuItemsShowsAllItemsWithStatus() throws Exception {
        MenuItem a = menu("K3 Alpha");
        MenuItem gone = menu("K3 Beta");
        gone.setStatus(MenuItemStatus.DISCONTINUED);
        persist(gone);

        String body = ok(call(HttpMethod.GET, "/menu-items", kitchenToken, null));
        List<Map<String, Object>> items = json(body, "$");
        Map<String, Object> first = items.stream().filter(i -> i.get("id").equals(a.getId().toString()))
                .findFirst().orElseThrow();
        assertThat(first).containsEntry("name", "K3 Alpha").containsEntry("status", "AVAILABLE")
                .containsEntry("categoryId", a.getCategory().getId().toString());
        assertThat(first.get("updatedAt")).isNotNull();
        assertThat(items).anyMatch(i -> i.get("id").equals(gone.getId().toString())
                && i.get("status").equals("DISCONTINUED"));
    }

    @Test
    void setStatusChangesMenuNotifiesWaiterBumpsRevisionAndAudits() throws Exception {
        MenuItem m = menu("K3 Pho");
        long rev = revision();

        String body = ok(call(HttpMethod.PATCH, "/menu-items/" + m.getId() + "/status", kitchenToken,
                statusBody("SOLD_OUT")));
        assertThat((String) json(body, "$.status")).isEqualTo("SOLD_OUT");
        assertThat((String) json(body, "$.id")).isEqualTo(m.getId().toString());
        assertThat(reload(m).getStatus()).isEqualTo(MenuItemStatus.SOLD_OUT);
        assertThat(revision()).isEqualTo(rev + 1);
        assertThat(count("select count(n) from Notification n where n.kind = 'MENU_AVAILABILITY' "
                + "and n.audienceRole = com.vertex.restosphere.common.enums.Role.WAITER and n.body = 'K3 Pho' "
                + "and n.path = :p0", m.getId().toString())).isEqualTo(1);
        assertThat(auditCount("set-menu-status")).isEqualTo(1);

        ok(call(HttpMethod.PATCH, "/menu-items/" + m.getId() + "/status", kitchenToken, statusBody("AVAILABLE")));
        assertThat(reload(m).getStatus()).isEqualTo(MenuItemStatus.AVAILABLE);
        assertThat(revision()).isEqualTo(rev + 2);
    }

    @Test
    void setStatusRejectsInvalidInputWithoutSideEffects() throws Exception {
        MenuItem m = menu("K3 A");
        MenuItem gone = menu("K3 Gone");
        gone.setStatus(MenuItemStatus.DISCONTINUED);
        gone = persist(gone);
        long rev = revision();
        String path = "/menu-items/" + m.getId() + "/status";

        call(HttpMethod.PATCH, path, kitchenToken, statusBody("DISCONTINUED")).andExpect(status().isBadRequest());
        call(HttpMethod.PATCH, path, kitchenToken, statusBody("BOGUS")).andExpect(status().isBadRequest());
        call(HttpMethod.PATCH, path, kitchenToken, "{}").andExpect(status().isBadRequest());
        call(HttpMethod.PATCH, "/menu-items/" + gone.getId() + "/status", kitchenToken, statusBody("AVAILABLE"))
                .andExpect(status().isBadRequest());
        call(HttpMethod.PATCH, "/menu-items/not-a-uuid/status", kitchenToken, statusBody("SOLD_OUT"))
                .andExpect(status().isBadRequest());
        call(HttpMethod.PATCH, "/menu-items/" + UUID.randomUUID() + "/status", kitchenToken,
                statusBody("SOLD_OUT")).andExpect(status().isNotFound());

        assertThat(reload(m).getStatus()).isEqualTo(MenuItemStatus.AVAILABLE);
        assertThat(reload(gone).getStatus()).isEqualTo(MenuItemStatus.DISCONTINUED);
        assertThat(revision()).isEqualTo(rev);
        assertThat(auditCount("set-menu-status")).isZero();
    }

    @Test
    void requestCreatesPendingApprovalWithoutChangingMenu() throws Exception {
        MenuItem m = menu("K3 Bun");
        long rev = revision();

        String body = created(call(HttpMethod.POST, "/menu-requests", kitchenToken,
                requestBody(m.getId(), "SOLD_OUT")));
        assertThat((String) json(body, "$.menuItemId")).isEqualTo(m.getId().toString());
        assertThat((String) json(body, "$.requested")).isEqualTo("SOLD_OUT");
        assertThat((String) json(body, "$.status")).isEqualTo("PENDING");
        assertThat((String) json(body, "$.requestedBy")).isEqualTo(kitchen.getId().toString());
        assertThat((Object) json(body, "$.requestedAt")).isNotNull();

        assertThat(reload(m).getStatus()).isEqualTo(MenuItemStatus.AVAILABLE);
        assertThat(revision()).isEqualTo(rev + 1);
        assertThat(auditCount("request-menu-status")).isEqualTo(1);
        var stored = tx.execute(s -> em.createQuery("from ApprovalRequest a where a.targetId = :t",
                ApprovalRequest.class).setParameter("t", m.getId()).getSingleResult());
        assertThat(stored.getType()).isEqualTo(ApprovalRequest.Type.MENU_STATUS);
        assertThat(stored.getTargetType()).isEqualTo("MENU_ITEM");
        assertThat(stored.getReason()).isEqualTo("Menu availability: SOLD_OUT");
        assertThat(stored.getMenuStatus()).isEqualTo("SOLD_OUT");
        assertThat(stored.getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void requestErrors() throws Exception {
        MenuItem m = menu("K3 Com");
        MenuItem gone = menu("K3 Gone2");
        gone.setStatus(MenuItemStatus.DISCONTINUED);
        gone = persist(gone);

        call(HttpMethod.POST, "/menu-requests", kitchenToken, requestBody(m.getId(), "DISCONTINUED"))
                .andExpect(status().isBadRequest());
        call(HttpMethod.POST, "/menu-requests", kitchenToken, requestBody(m.getId(), "BOGUS"))
                .andExpect(status().isBadRequest());
        call(HttpMethod.POST, "/menu-requests", kitchenToken, "{\"status\":\"SOLD_OUT\"}")
                .andExpect(status().isBadRequest());
        call(HttpMethod.POST, "/menu-requests", kitchenToken, requestBody(gone.getId(), "AVAILABLE"))
                .andExpect(status().isBadRequest());
        call(HttpMethod.POST, "/menu-requests", kitchenToken, requestBody("not-a-uuid", "SOLD_OUT"))
                .andExpect(status().isBadRequest());
        call(HttpMethod.POST, "/menu-requests", kitchenToken, requestBody(UUID.randomUUID(), "SOLD_OUT"))
                .andExpect(status().isNotFound());
        assertThat(count("select count(a) from ApprovalRequest a where a.requestedBy.id = :p0", kitchen.getId()))
                .isZero();

        created(call(HttpMethod.POST, "/menu-requests", kitchenToken, requestBody(m.getId(), "SOLD_OUT")));
        long rev = revision();
        call(HttpMethod.POST, "/menu-requests", kitchenToken, requestBody(m.getId(), "SOLD_OUT"))
                .andExpect(status().isConflict());
        assertThat(revision()).isEqualTo(rev);
        assertThat(count("select count(a) from ApprovalRequest a where a.requestedBy.id = :p0", kitchen.getId()))
                .isEqualTo(1);
    }

    @Test
    void listRequestsReturnsOnlyMenuStatusRequestsWithDecision() throws Exception {
        MenuItem a = menu("K3 A");
        MenuItem b = menu("K3 B");
        ApprovalRequest decided = new ApprovalRequest();
        decided.setType(ApprovalRequest.Type.MENU_STATUS);
        decided.setTargetType("MENU_ITEM");
        decided.setTargetId(a.getId());
        decided.setReason("Menu availability: SOLD_OUT");
        decided.setMenuStatus("SOLD_OUT");
        decided.setRequestedBy(kitchen);
        decided.setStatus(ApprovalRequest.Status.APPROVED);
        decided.setDecidedBy(waiter);
        decided.setDecidedAt(java.time.Instant.now());
        decided = persist(decided);
        ApprovalRequest other = new ApprovalRequest();
        other.setType(ApprovalRequest.Type.DISCOUNT);
        other.setTargetType("BILL");
        other.setTargetId(UUID.randomUUID());
        other.setReason("x");
        other.setRequestedBy(kitchen);
        persist(other);
        created(call(HttpMethod.POST, "/menu-requests", kitchenToken, requestBody(b.getId(), "SOLD_OUT")));

        String body = ok(call(HttpMethod.GET, "/menu-requests", kitchenToken, null));
        List<Map<String, Object>> list = json(body, "$");
        assertThat(list).hasSize(2);
        assertThat(list.get(0)).containsEntry("id", decided.getId().toString())
                .containsEntry("menuItemId", a.getId().toString()).containsEntry("requested", "SOLD_OUT")
                .containsEntry("status", "APPROVED").containsEntry("decidedBy", waiter.getId().toString());
        assertThat(list.get(0).get("decidedAt")).isNotNull();
        assertThat(list.get(1)).containsEntry("menuItemId", b.getId().toString()).containsEntry("status", "PENDING");
    }
}
