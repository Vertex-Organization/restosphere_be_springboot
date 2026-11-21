package com.vertex.restosphere.mapper;

import static com.vertex.restosphere.common.util.Values.code;
import static com.vertex.restosphere.common.util.Values.id;
import static com.vertex.restosphere.common.util.Values.map;

import com.vertex.restosphere.entity.Incident;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Sự cố bếp dạng JSON; {@code managerIds} là những người được báo. */
@Component
public class IncidentMapper {

    public Map<String, Object> toMap(Incident i, List<String> managerIds) {
        var item = i.getOrderItem();
        return map(
                "id",
                id(i),
                "code",
                code("I", i),
                "type",
                i.getType(),
                "severity",
                i.getSeverity(),
                "status",
                i.getStatus(),
                "title",
                i.getTitle(),
                "description",
                i.getDescription(),
                "orderId",
                item == null ? null : id(item.getOrder()),
                "itemId",
                item == null ? null : id(item),
                "reporterId",
                id(i.getReporter()),
                "assigneeId",
                id(i.getAssignee()),
                "at",
                i.getCreatedAt(),
                "notifiedStaffIds",
                managerIds,
                "response",
                i.getResolution() == null
                        ? null
                        : map(
                                "by",
                                id(i.getAssignee()),
                                "at",
                                i.getUpdatedAt(),
                                "text",
                                i.getResolution()));
    }
}
