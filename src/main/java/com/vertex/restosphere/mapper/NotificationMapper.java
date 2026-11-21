package com.vertex.restosphere.mapper;

import static com.vertex.restosphere.common.util.Values.id;
import static com.vertex.restosphere.common.util.Values.map;
import static com.vertex.restosphere.common.util.Values.str;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.entity.Notification;
import com.vertex.restosphere.entity.NotificationRead;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Thông báo dạng JSON; trường đích (session, bill, order...) suy ra từ vai trò nhận. */
@Component
public class NotificationMapper {

    private static final Set<String> KITCHEN_ORDER_KINDS = Set.of("NEW_ORDER", "PRIORITY");

    public Map<String, Object> toMap(Notification n, List<NotificationRead> reads) {
        String target = n.getPath();
        Role audience = n.getAudienceRole();
        boolean decision = "REQUEST_DECIDED".equals(n.getKind());
        return map(
                "id",
                id(n),
                "kind",
                n.getKind(),
                "title",
                n.getTitle(),
                "body",
                n.getBody(),
                "at",
                n.getCreatedAt(),
                "path",
                path(n, target),
                "audience",
                audience,
                "staffId",
                id(n.getRecipient()),
                "targetId",
                target,
                "requestId",
                decision ? target : null,
                "sessionId",
                audience == Role.WAITER ? target : null,
                "billId",
                audience == Role.CASHIER && !decision ? target : null,
                "orderId",
                audience == Role.KITCHEN && KITCHEN_ORDER_KINDS.contains(n.getKind())
                        ? target
                        : null,
                "readBy",
                reads.stream().map(r -> id(r.getUser())).toList());
    }

    private static String path(Notification n, String target) {
        if (n.getAudienceRole() != Role.MANAGER) {
            return str(target);
        }
        String page = "INCIDENT".equals(n.getKind()) ? "incidents" : "approvals";
        return "/staff/manager/" + page + "?id=" + str(target);
    }
}
