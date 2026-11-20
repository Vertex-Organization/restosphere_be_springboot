package com.vertex.restosphere;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vertex.restosphere.entity.ApprovalRequest;
import com.vertex.restosphere.entity.Bill;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class CashierDiscountApiIntegrationTests extends CashierApiTestBase {
    @Override
    String prefix() {
        return "c5";
    }

    private String body(long version, String mode, long value, String reason) {
        return "{\"billVersion\":%d,\"discount\":{\"mode\":\"%s\",\"value\":%d,\"reason\":\"%s\"}}".formatted(version,
                mode, value, reason);
    }

    private String path(Fixture f) {
        return "/bills/" + f.bill().getId() + "/discount";
    }

    private long version(Fixture f) {
        return tx.execute(s -> em.find(Bill.class, f.bill().getId()).getVersion());
    }

    @Test
    void authIsEnforced() throws Exception {
        Fixture f = billFixture();
        call(HttpMethod.PUT, path(f), null, body(0, "AMOUNT", 1, "r")).andExpect(status().isUnauthorized());
        call(HttpMethod.PUT, path(f), waiterToken, body(0, "AMOUNT", 1, "r")).andExpect(status().isForbidden());
        call(HttpMethod.DELETE, path(f) + "?billVersion=0", waiterToken, null).andExpect(status().isForbidden());
        call(HttpMethod.POST, path(f) + "-requests", waiterToken, body(0, "AMOUNT", 1, "r"))
                .andExpect(status().isForbidden());
    }

    @Test
    void applyPercentUpToTenPercentRecomputesTotalsAndBumpsRevision() throws Exception {
        Fixture f = billFixture();
        long before = revision();
        String res = ok(call(HttpMethod.PUT, path(f), cashierToken, body(version(f), "PERCENT", 10, "regular")));
        assertThat(this.<String>json(res, "$.discount.mode")).isEqualTo("PERCENT");
        assertThat(this.<String>json(res, "$.discount.reason")).isEqualTo("regular");
        assertThat(this.<String>json(res, "$.discount.appliedBy")).isEqualTo(cashier.getId().toString());
        assertThat(this.<Number>json(res, "$.totals.discount").longValue()).isEqualTo(10000L);
        assertThat(this.<Number>json(res, "$.totals.total").longValue()).isEqualTo(90000L);
        assertThat(this.<Number>json(res, "$.remaining").longValue()).isEqualTo(90000L);
        assertThat(revision()).isEqualTo(before + 1);
        assertThat(auditCount("apply-discount")).isEqualTo(1);
    }

    @Test
    void applyAmountAboveTenPercentNeedsApprovalAndInvalidInputIs400() throws Exception {
        Fixture f = billFixture();
        long v = version(f);
        call(HttpMethod.PUT, path(f), cashierToken, body(v, "AMOUNT", 10001, "r")).andExpect(status().isConflict());
        call(HttpMethod.PUT, path(f), cashierToken, body(v, "PERCENT", 11, "r")).andExpect(status().isConflict());
        call(HttpMethod.PUT, path(f), cashierToken, body(v, "PERCENT", 101, "r")).andExpect(status().isBadRequest());
        call(HttpMethod.PUT, path(f), cashierToken, body(v, "AMOUNT", 100001, "r")).andExpect(status().isBadRequest());
        call(HttpMethod.PUT, path(f), cashierToken, body(v, "AMOUNT", 0, "r")).andExpect(status().isBadRequest());
        call(HttpMethod.PUT, path(f), cashierToken, body(v, "BOGUS", 5, "r")).andExpect(status().isBadRequest());
        call(HttpMethod.PUT, path(f), cashierToken, body(v, "AMOUNT", 5, " ")).andExpect(status().isBadRequest());
        call(HttpMethod.PUT, path(f), cashierToken, "{\"billVersion\":%d}".formatted(v))
                .andExpect(status().isBadRequest());
        call(HttpMethod.PUT, "/bills/" + UUID.randomUUID() + "/discount", cashierToken, body(0, "AMOUNT", 5, "r"))
                .andExpect(status().isNotFound());
    }

    @Test
    void staleVersionIsConflictAndBillWithPaymentIsNotEditable() throws Exception {
        Fixture f = billFixture();
        openShift(cashierToken, 0);
        call(HttpMethod.PUT, path(f), cashierToken, body(version(f) + 9, "AMOUNT", 5, "r"))
                .andExpect(status().isConflict());
        created(call(HttpMethod.POST, "/bills/" + f.bill().getId() + "/payments/cash", cashierToken,
                "{\"billVersion\":%d,\"amount\":1000,\"tendered\":1000,\"idempotencyKey\":\"c5-pay\"}"
                        .formatted(version(f))));
        call(HttpMethod.PUT, path(f), cashierToken, body(version(f), "AMOUNT", 5, "r"))
                .andExpect(status().isConflict());
        call(HttpMethod.DELETE, path(f) + "?billVersion=" + version(f), cashierToken, null)
                .andExpect(status().isConflict());
        call(HttpMethod.POST, path(f) + "-requests", cashierToken, body(version(f), "AMOUNT", 5, "r"))
                .andExpect(status().isConflict());
    }

    @Test
    void removeClearsTheDiscount() throws Exception {
        Fixture f = billFixture();
        String applied = ok(call(HttpMethod.PUT, path(f), cashierToken, body(version(f), "AMOUNT", 5000, "promo")));
        long newVersion = this.<Number>json(applied, "$.version").longValue();
        assertThat(newVersion).isEqualTo(version(f));
        long before = revision();

        String res = ok(call(HttpMethod.DELETE, path(f) + "?billVersion=" + newVersion, cashierToken, null));
        assertThat(this.<Number>json(res, "$.totals.discount").longValue()).isZero();
        assertThat(this.<Number>json(res, "$.totals.total").longValue()).isEqualTo(100000L);
        assertThat(revision()).isEqualTo(before + 1);
        assertThat(auditCount("remove-discount")).isEqualTo(1);
        call(HttpMethod.DELETE, path(f), cashierToken, null).andExpect(status().isBadRequest());
        call(HttpMethod.DELETE, path(f) + "?billVersion=1", cashierToken, null).andExpect(status().isConflict());
    }

    @Test
    void requestCreatesPendingApprovalAndRejectsDuplicates() throws Exception {
        Fixture f = billFixture();
        long before = revision();
        String res = created(call(HttpMethod.POST, path(f) + "-requests", cashierToken,
                body(version(f), "PERCENT", 20, "vip guest")));
        assertThat(this.<String>json(res, "$.type")).isEqualTo("DISCOUNT");
        assertThat(this.<String>json(res, "$.status")).isEqualTo("PENDING");
        assertThat(this.<String>json(res, "$.billId")).isEqualTo(f.bill().getId().toString());
        assertThat(this.<Number>json(res, "$.discount.amount").longValue()).isEqualTo(20000L);
        assertThat(this.<String>json(res, "$.reason")).isEqualTo("vip guest");
        assertThat(this.<String>json(res, "$.requestedBy")).isEqualTo(cashier.getId().toString());
        assertThat(revision()).isEqualTo(before + 1);
        assertThat(auditCount("request-discount")).isEqualTo(1);
        assertThat(count("select count(a) from ApprovalRequest a where a.type = :p0 and a.targetId = :p1",
                ApprovalRequest.Type.DISCOUNT, f.bill().getId())).isEqualTo(1);

        call(HttpMethod.POST, path(f) + "-requests", cashierToken, body(version(f), "PERCENT", 30, "again"))
                .andExpect(status().isConflict());
        call(HttpMethod.POST, path(f) + "-requests", cashierToken, body(version(f), "PERCENT", 101, "r"))
                .andExpect(status().isBadRequest());
    }
}
