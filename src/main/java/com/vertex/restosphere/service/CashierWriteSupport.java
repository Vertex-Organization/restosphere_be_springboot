package com.vertex.restosphere.service;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.entity.Bill;
import com.vertex.restosphere.entity.Payment;
import com.vertex.restosphere.entity.RestaurantSettings;
import com.vertex.restosphere.entity.User;
import com.vertex.restosphere.repository.RestaurantSettingsRepository;
import java.math.BigDecimal;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Shared tail of every cashier write command (same as WorkspaceService.command): bump the revision so polling
 * clients notice the change, and write the WORKSPACE audit row. Call inside a transaction that holds
 * RestaurantLock.
 */
@Component
@RequiredArgsConstructor
class CashierWriteSupport {

    private final RestaurantSettingsRepository settings;
    private final WorkspaceStore store;
    private final ServiceWorkflow workflow;

    void commit(User user, String commandType) {
        RestaurantSettings s = settings.findBySingletonKey(1).orElseGet(store::lock);
        s.setRevision(s.getRevision() + 1);
        workflow.audit(user, commandType, user.getId(), "WORKSPACE", commandType);
    }

    /** Tell the waiter once the payment settled the whole bill (pay-cash / confirm-transfer). */
    void notifyIfPaid(Payment payment) {
        Bill bill = payment.getBill();
        if (bill.getStatus() == Bill.Status.PAID) {
            workflow.notify(Role.WAITER, "PAYMENT", "Payment received", bill.getCode(),
                    bill.getSession().getId().toString());
        }
    }

    /** Whole-dong amount rule of the legacy command, reused from BillingWorkflow. */
    static BigDecimal money(BigDecimal value, String key) {
        return BillingWorkflow.money(Map.of(key, value), key);
    }
}
