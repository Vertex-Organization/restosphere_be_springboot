package com.vertex.restosphere.service;

import com.vertex.restosphere.entity.Bill;
import com.vertex.restosphere.entity.Payment;
import com.vertex.restosphere.entity.User;
import java.math.BigDecimal;
import java.math.RoundingMode;

public interface CashierWriteSupport {

    void commit(User user, String commandType);

    void notifyIfPaid(Payment payment);

    void version(Bill bill, long billVersion);

    void settle(Bill bill);

    static BigDecimal whole(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP);
    }

    static BigDecimal money(BigDecimal value, String key) {
        BigDecimal n;
        try {
            n = new BigDecimal(value.toString());
        } catch (Exception e) {
            throw com.vertex.restosphere.service.impl.WorkspaceStoreImpl.invalid("Invalid " + key);
        }
        if (n.signum() < 0 || n.scale() > 0 || n.compareTo(new BigDecimal("999999999999")) > 0) {
            throw com.vertex.restosphere.service.impl.WorkspaceStoreImpl.invalid("Invalid whole-dong amount: " + key);
        }
        return n;
    }
}
