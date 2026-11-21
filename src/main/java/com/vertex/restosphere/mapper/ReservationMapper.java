package com.vertex.restosphere.mapper;

import static com.vertex.restosphere.common.util.Values.id;
import static com.vertex.restosphere.common.util.Values.map;

import com.vertex.restosphere.entity.Reservation;
import com.vertex.restosphere.entity.ReservationHistory;
import com.vertex.restosphere.entity.ReservationItem;
import com.vertex.restosphere.entity.ReservationTable;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Lịch đặt bàn dạng JSON cho nhân viên và cho khách tra cứu. */
@Component
public class ReservationMapper {

    public static final Comparator<ReservationHistory> HISTORY_ORDER =
            Comparator.comparing(ReservationHistory::getCreatedAt)
                    .thenComparing(h -> h.getId().toString());

    /** Bản đầy đủ cho nhân viên; {@code history} phải đã sắp theo {@link #HISTORY_ORDER}. */
    public Map<String, Object> toStaffMap(
            Reservation r,
            ReservationTable assignment,
            List<ReservationItem> preorder,
            List<ReservationHistory> history) {
        return map(
                "id",
                id(r),
                "code",
                r.getCode(),
                "customer",
                map("name", r.getCustomerName(), "phone", r.getPhone()),
                "startsAt",
                r.getStartsAt(),
                "guests",
                r.getGuestCount(),
                "duration",
                Duration.between(r.getStartsAt(), r.getEndsAt()).toMinutes(),
                "areaId",
                id(r.getArea()),
                "tableId",
                assignment == null ? null : id(assignment.getTable()),
                "occasion",
                r.getOccasion(),
                "email",
                r.getEmail(),
                "notes",
                r.getNote(),
                "specialRequests",
                Objects.toString(r.getSpecialRequests(), ""),
                "status",
                r.getStatus(),
                "source",
                r.getSource(),
                "actualGuests",
                r.getActualGuests(),
                "arrivedAt",
                r.getArrivedAt(),
                "handedOverAt",
                r.getHandedOverAt(),
                "preorder",
                preorder.stream()
                        .map(
                                i ->
                                        map(
                                                "menuItemId",
                                                id(i.getMenuItem()),
                                                "quantity",
                                                i.getQuantity(),
                                                "note",
                                                i.getNote()))
                        .toList(),
                "history",
                history.stream()
                        .map(h -> map("at", h.getCreatedAt(), "text", h.getDescription()))
                        .toList());
    }

    /** Bản rút gọn cho khách tra cứu bằng mã và số điện thoại. */
    public Map<String, Object> toPublicMap(
            Reservation r, List<ReservationItem> items, ZoneId zone) {
        var local = r.getStartsAt().atZone(zone);
        return map(
                "code",
                r.getCode(),
                "status",
                r.getStatus(),
                "createdAt",
                r.getCreatedAt(),
                "startsAt",
                r.getStartsAt(),
                "durationMinutes",
                Duration.between(r.getStartsAt(), r.getEndsAt()).toMinutes(),
                "areaId",
                id(r.getArea()),
                "areaName",
                r.getArea().getName(),
                "date",
                local.toLocalDate(),
                "time",
                local.toLocalTime().toString(),
                "guests",
                r.getGuestCount(),
                "name",
                r.getCustomerName(),
                "phone",
                r.getPhone(),
                "email",
                r.getEmail(),
                "note",
                r.getNote(),
                "occasion",
                r.getOccasion(),
                "items",
                items.stream()
                        .map(
                                i ->
                                        map(
                                                "dishId",
                                                id(i.getMenuItem()),
                                                "name",
                                                i.getMenuItem().getName(),
                                                "quantity",
                                                i.getQuantity()))
                        .toList());
    }
}
