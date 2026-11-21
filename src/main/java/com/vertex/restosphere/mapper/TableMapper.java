package com.vertex.restosphere.mapper;

import static com.vertex.restosphere.common.util.Values.id;
import static com.vertex.restosphere.common.util.Values.map;

import com.vertex.restosphere.entity.Area;
import com.vertex.restosphere.entity.ReservationTable;
import com.vertex.restosphere.entity.RestaurantTable;
import com.vertex.restosphere.entity.TableSession;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Khu vực, bàn, phiên phục vụ và phân bàn dạng JSON cho các workspace. */
@Component
public class TableMapper {

    public Map<String, Object> toMap(Area a) {
        return map("id", id(a), "name", a.getName());
    }

    public Map<String, Object> toMap(RestaurantTable t, TableSession openSession) {
        return map(
                "id",
                id(t),
                "code",
                t.getCode(),
                "areaId",
                id(t.getArea()),
                "area",
                t.getArea().getName(),
                "capacity",
                t.getCapacity(),
                "seats",
                t.getCapacity(),
                "status",
                t.getStatus(),
                "sessionId",
                openSession == null ? null : id(openSession));
    }

    public Map<String, Object> toMap(TableSession t) {
        return map(
                "id",
                id(t),
                "tableId",
                id(t.getTable()),
                "reservationId",
                id(t.getReservation()),
                "waiterId",
                id(t.getWaiter()),
                "guests",
                t.getGuestCount(),
                "startedAt",
                t.getStartedAt(),
                "openedAt",
                t.getStartedAt(),
                "handedOverAt",
                t.getStartedAt(),
                "acceptedAt",
                t.getAcceptedAt(),
                "guestLeftAt",
                t.getGuestLeftAt(),
                "closedAt",
                t.getEndedAt(),
                "paymentRequested",
                t.getStatus() == TableSession.Status.PAYMENT_REQUESTED);
    }

    /** {@code releasedAt} chỉ có khi lịch đặt đã hết hiệu lực. */
    public Map<String, Object> toMap(ReservationTable a, boolean reservationActive) {
        return map(
                "id",
                id(a),
                "reservationId",
                id(a.getReservation()),
                "tableId",
                id(a.getTable()),
                "assignedAt",
                a.getCreatedAt(),
                "releasedAt",
                reservationActive ? null : a.getReservation().getUpdatedAt());
    }
}
