package com.vertex.restosphere.dto.response;

import com.vertex.restosphere.entity.Incident;
import com.vertex.restosphere.entity.Order;
import com.vertex.restosphere.entity.OrderItem;
import com.vertex.restosphere.entity.User;
import java.time.Instant;
import java.util.UUID;

public record ManagerIncidentResponse(UUID id, String code, String type, Incident.Severity severity,
        Incident.Status status, String title, String description, UUID orderId, UUID itemId, String orderLabel,
        String itemName, UUID reporterId, String reporterName, UUID assigneeId, String assigneeName, Instant at,
        Reply response) {

    public record Reply(UUID by, Instant at, String text) {
    }

    public static String code(Incident i) {
        return "I-" + i.getId().toString().substring(0, 8).toUpperCase();
    }

    public static ManagerIncidentResponse from(Incident i) {
        OrderItem item = i.getOrderItem();
        Order order = item == null ? null : item.getOrder();
        User assignee = i.getAssignee();
        UUID assigneeId = assignee == null ? null : assignee.getId();
        return new ManagerIncidentResponse(i.getId(), code(i), i.getType(), i.getSeverity(), i.getStatus(),
                i.getTitle(), i.getDescription(), order == null ? null : order.getId(),
                item == null ? null : item.getId(),
                order == null ? null
                        : "Bàn " + order.getSession().getTable().getCode() + " · Lượt " + order.getRoundNumber(),
                item == null ? null : item.getItemName(), i.getReporter().getId(), i.getReporter().getFullName(),
                assigneeId, assignee == null ? null : assignee.getFullName(), i.getCreatedAt(),
                i.getResolution() == null ? null : new Reply(assigneeId, i.getUpdatedAt(), i.getResolution()));
    }
}
