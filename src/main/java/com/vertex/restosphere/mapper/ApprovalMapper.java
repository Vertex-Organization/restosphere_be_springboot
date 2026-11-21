package com.vertex.restosphere.mapper;

import static com.vertex.restosphere.common.util.Values.code;
import static com.vertex.restosphere.common.util.Values.id;
import static com.vertex.restosphere.common.util.Values.map;

import com.vertex.restosphere.entity.ApprovalRequest;
import com.vertex.restosphere.entity.Bill;
import com.vertex.restosphere.entity.OrderItem;
import com.vertex.restosphere.entity.Payment;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Yêu cầu phê duyệt dạng JSON; đối tượng liên quan do nơi gọi tải sẵn. */
@Component
public class ApprovalMapper {

    /** {@code bill}, {@code item}, {@code payment} có thể null tùy loại yêu cầu. */
    public Map<String, Object> toMap(
            ApprovalRequest r, Bill bill, OrderItem item, Payment payment) {
        return map(
                "id",
                id(r),
                "code",
                code("R", r),
                "type",
                r.getType(),
                "status",
                r.getStatus(),
                "targetType",
                r.getTargetType(),
                "targetId",
                r.getTargetId(),
                "billId",
                bill == null ? null : id(bill),
                "paymentId",
                payment == null ? null : id(payment),
                "itemId",
                item == null ? null : id(item),
                "sessionId",
                bill == null ? null : id(bill.getSession()),
                "amount",
                r.getAmount(),
                "menuStatus",
                r.getMenuStatus(),
                "discount",
                r.getDiscountValue() == null
                        ? null
                        : map(
                                "mode",
                                r.getDiscountMode(),
                                "value",
                                r.getDiscountValue(),
                                "amount",
                                r.getAmount()),
                "reason",
                r.getReason(),
                "requestedBy",
                id(r.getRequestedBy()),
                "requestedAt",
                r.getCreatedAt(),
                "decidedBy",
                r.getDecidedBy() == null ? null : id(r.getDecidedBy()),
                "decidedAt",
                r.getDecidedAt(),
                "decisionNote",
                r.getDecisionNote());
    }
}
