package com.vertex.restosphere.service.impl;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.entity.Bill;
import com.vertex.restosphere.entity.Payment;
import com.vertex.restosphere.entity.RestaurantSettings;
import com.vertex.restosphere.entity.User;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.repository.RestaurantSettingsRepository;
import com.vertex.restosphere.service.BillingWorkflow;
import com.vertex.restosphere.service.CashierWriteSupport;
import com.vertex.restosphere.service.ServiceWorkflow;
import com.vertex.restosphere.service.WorkspaceStore;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CashierWriteSupportImpl implements CashierWriteSupport {

    private final RestaurantSettingsRepository settings;
    private final WorkspaceStore store;
    private final ServiceWorkflow workflow;
    private final BillingWorkflow billing;

    @Override
    public void commit(User user, String commandType) {
        RestaurantSettings s = settings.findBySingletonKey(1).orElseGet(store::lock);
        s.setRevision(s.getRevision() + 1);
        workflow.audit(user, commandType, user.getId(), "WORKSPACE", commandType);
    }

    @Override
    public void notifyIfPaid(Payment payment) {
        Bill bill = payment.getBill();
        if (bill.getStatus() == Bill.Status.PAID) {
            workflow.notify(Role.WAITER, "PAYMENT", "Payment received", bill.getCode(),
                    bill.getSession().getId().toString());
        }
    }

    @Override
    public void version(Bill bill, long billVersion) {
        if (billVersion != bill.getVersion()) {
            throw ApiException.conflict("Bill changed; refresh before retrying");
        }
    }

    @Override
    public void settle(Bill bill) {
        if (billing.received(bill).compareTo(bill.getTotal()) >= 0) {
            bill.setStatus(Bill.Status.PAID);
            bill.setPaidAt(Instant.now());
        } else {
            bill.setStatus(Bill.Status.OPEN);
            bill.setPaidAt(null);
        }
    }
}
