package com.vertex.restosphere.service;

import com.vertex.restosphere.entity.ApprovalRequest;
import com.vertex.restosphere.entity.Bill;
import com.vertex.restosphere.entity.OrderItem;
import com.vertex.restosphere.entity.Payment;
import com.vertex.restosphere.entity.TableSession;
import com.vertex.restosphere.entity.User;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Hoa don, thanh toan, yeu cau phe duyet cua workspace command. Helper tinh (whole, money) o BillingWorkflowImpl. */
public interface BillingWorkflow {

    Bill bill(TableSession session);

    List<OrderItem> items(Bill bill);

    void recalculate(Bill b);

    BigDecimal received(Bill b);

    void editable(Bill b);

    String command(String type, Map<String, Object> c, User user);

    void applyDiscount(Bill b, String mode, BigDecimal value, String reason, User user);

    ApprovalRequest request(ApprovalRequest.Type type, String target, UUID targetId, BigDecimal amount, String reason,
            User user);

    void decide(Map<String, Object> c, User user);

    Map<String, Object> view(Bill b);

    Map<String, Object> payment(Payment p);
}
