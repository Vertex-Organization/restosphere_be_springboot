package com.vertex.restosphere.mapper;

import static com.vertex.restosphere.common.util.Values.id;
import static com.vertex.restosphere.common.util.Values.map;

import com.vertex.restosphere.entity.AuditLog;
import com.vertex.restosphere.entity.Payment;
import com.vertex.restosphere.entity.Reservation;
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

    public static final Comparator<AuditLog> HISTORY_ORDER =
            Comparator.comparing(AuditLog::getCreatedAt).thenComparing(h -> h.getId().toString());

    /** Bản đầy đủ cho nhân viên; {@code history} phải đã sắp theo {@link #HISTORY_ORDER}. */
    public Map<String, Object> toStaffMap(
            Reservation r,
            ReservationTable assignment,
            List<ReservationItem> preorder,
            List<AuditLog> history) {
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
                "deposit",
                deposit(r),
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

    /** Tiền cọc của lịch; null khi lịch không cần cọc. */
    static Map<String, Object> deposit(Reservation r) {
        if (r.getDepositAmount() == null) return null;
        return map(
                "amount",
                r.getDepositAmount(),
                "dueAt",
                r.getDepositDueAt(),
                "paidAt",
                r.depositPaidAt(),
                "reference",
                r.depositReference(),
                "cashRequestedAt",
                r.getDepositCashRequestedAt(),
                "payos",
                payosLink(r.livePayosPayment()));
    }

    /** Link payOS còn trả được của khoản cọc; null khi chưa tạo hoặc đã trả. */
    static Map<String, Object> payosLink(Payment p) {
        if (p == null) return null;
        return map(
                "paymentId",
                id(p),
                "orderCode",
                p.getPayosOrderCode(),
                "checkoutUrl",
                p.getCheckoutUrl(),
                "qrCode",
                p.getQrCode());
    }

    /** Bản rút gọn cho khách tra cứu bằng mã và số điện thoại. */
    public Map<String, Object> toPublicMap(
            Reservation r, List<ReservationItem> items, ZoneId zone) {
        var local = r.getStartsAt().atZone(zone);
        return map(
                "deposit",
                deposit(r),
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
                r.areaName(),
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
