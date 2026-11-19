package com.vertex.restosphere.service;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.dto.response.ManagerReportResponse;
import com.vertex.restosphere.dto.response.ManagerReportResponse.Daily;
import com.vertex.restosphere.dto.response.ManagerReportResponse.Dish;
import com.vertex.restosphere.dto.response.ManagerReportResponse.Operations;
import com.vertex.restosphere.dto.response.ManagerReportResponse.Summary;
import com.vertex.restosphere.entity.Bill;
import com.vertex.restosphere.entity.OrderItem;
import com.vertex.restosphere.entity.Payment;
import com.vertex.restosphere.entity.Reservation;
import com.vertex.restosphere.entity.RestaurantSettings;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.repository.ReportQueryRepository;
import com.vertex.restosphere.repository.RestaurantSettingsRepository;
import com.vertex.restosphere.security.CurrentUserService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bao cao doanh thu trang Manager (chi doc); thay phan FE tu tinh tu "bills"/"payments" cua snapshot (snapshot van giu).
 * Du lieu = toi da 3 query (giao dich+hoa don+phien, mon, dem dat ban); phan con lai tinh bang Java.
 */
@Service
@RequiredArgsConstructor
public class ManagerReportService {

    private static final Set<OrderItem.Status> EXCLUDED_ITEMS =
            Set.of(OrderItem.Status.CANCELLED, OrderItem.Status.VOIDED, OrderItem.Status.REJECTED);
    private static final Set<Reservation.Status> ARRIVED =
            Set.of(Reservation.Status.ARRIVED, Reservation.Status.SEATED, Reservation.Status.COMPLETED);
    private static final Set<Reservation.Status> CANCELLED_OR_NO_SHOW =
            Set.of(Reservation.Status.CANCELLED, Reservation.Status.NO_SHOW);

    private final CurrentUserService currentUser;
    private final ReportQueryRepository reports;
    private final RestaurantSettingsRepository settings;

    @Transactional(readOnly = true)
    public ManagerReportResponse report(UUID actorId, LocalDate from, LocalDate to, String shift,
            Payment.Method method) {
        currentUser.require(actorId, Role.ADMIN, Role.MANAGER);
        if (shift != null && !shift.equals("LUNCH") && !shift.equals("DINNER")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Shift must be LUNCH or DINNER");
        }
        ZoneId zone = settings.findBySingletonKey(1).map(RestaurantSettings::getTimezone).map(ZoneId::of)
                .orElse(ZoneId.of("Asia/Ho_Chi_Minh"));
        LocalDate end = to == null ? LocalDate.now(zone) : to;
        LocalDate start = from == null ? end.minusDays(6) : from;
        if (start.isAfter(end)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "From date must be on or before to date");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > 366) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Date range must not exceed 366 days");
        }
        Instant fromAt = start.atStartOfDay(zone).toInstant();
        Instant toAt = end.plusDays(1).atStartOfDay(zone).toInstant();

        // B1: giao dich COMPLETED trong khoang (dung chung moi phan).
        List<Payment> inRange = reports.findPaymentsWithBillBetween(Payment.Status.COMPLETED, fromAt, toAt);
        // B2: loc them method/shift; shift xet theo gio tra cua hoa don PAID.
        List<Payment> filtered = inRange.stream()
                .filter(p -> method == null || p.getMethod() == method)
                .filter(p -> shift == null || (p.getBill().getStatus() == Bill.Status.PAID
                        && shift.equals(shiftOf(p.getBill(), zone))))
                .toList();
        // B3: hoa don PAID co it nhat 1 giao dich da loc, moi hoa don 1 lan.
        Collection<Bill> bills = paidBills(filtered);

        return new ManagerReportResponse(start, end, summary(filtered, bills), daily(filtered, zone), dishes(bills),
                operations(paidBills(inRange), fromAt, toAt));
    }

    /** Gio tra < 16 = LUNCH, nguoc lai DINNER (giong snapshot cu); paidAt null = khong thuoc ca nao. */
    static String shiftOf(Bill bill, ZoneId zone) {
        if (bill.getPaidAt() == null) {
            return null;
        }
        return bill.getPaidAt().atZone(zone).getHour() < 16 ? "LUNCH" : "DINNER";
    }

    /** Ngay cua giao dich theo gio nha hang (23:30 +7 van la ngay do, khong nhay sang ngay UTC). */
    static LocalDate dayOf(Payment p, ZoneId zone) {
        return atOf(p).atZone(zone).toLocalDate();
    }

    private static Instant atOf(Payment p) {
        return p.getPaidAt() == null ? p.getCreatedAt() : p.getPaidAt();
    }

    private static Collection<Bill> paidBills(List<Payment> payments) {
        return payments.stream().map(Payment::getBill).filter(b -> b.getStatus() == Bill.Status.PAID)
                .collect(Collectors.toMap(Bill::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new))
                .values();
    }

    private static long guests(Collection<Bill> bills) {
        return bills.stream().mapToLong(b -> b.getSession().getGuestCount()).sum();
    }

    private static BigDecimal revenue(Collection<Payment> payments) {
        return payments.stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static Summary summary(List<Payment> payments, Collection<Bill> bills) {
        BigDecimal revenue = revenue(payments);
        BigDecimal average = bills.isEmpty() ? BigDecimal.ZERO
                : revenue.divide(BigDecimal.valueOf(bills.size()), 0, RoundingMode.HALF_UP);
        return new Summary(revenue, payments.size(), bills.size(), average, guests(bills));
    }

    private static List<Daily> daily(List<Payment> payments, ZoneId zone) {
        // TreeMap: chi ngay co giao dich, tang dan.
        return payments.stream().collect(Collectors.groupingBy(p -> dayOf(p, zone), TreeMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(e -> new Daily(e.getKey(), e.getValue().size(), revenue(e.getValue())))
                .toList();
    }

    /** Doanh thu mon = quantity * unitPrice (truoc giam gia), gom theo ten mon. */
    private List<Dish> dishes(Collection<Bill> bills) {
        if (bills.isEmpty()) {
            return List.of();
        }
        Set<UUID> sessionIds = bills.stream().map(b -> b.getSession().getId()).collect(Collectors.toSet());
        Map<String, List<OrderItem>> byName = reports.findItemsBySessionIdIn(sessionIds, EXCLUDED_ITEMS).stream()
                .collect(Collectors.groupingBy(OrderItem::getItemName));
        return byName.entrySet().stream()
                .map(e -> new Dish(e.getKey(),
                        e.getValue().stream().map(OrderItem::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add),
                        e.getValue().stream().map(i -> i.getQuantity().multiply(i.getUnitPrice()))
                                .reduce(BigDecimal.ZERO, BigDecimal::add)))
                .sorted(Comparator.comparing(Dish::quantity).reversed()
                        .thenComparing(Comparator.comparing(Dish::revenue).reversed())
                        .thenComparing(Dish::name))
                .toList();
    }

    /** Khong ap shift/method: khach da tra trong khoang + thong ke dat ban theo startsAt. */
    private Operations operations(Collection<Bill> paidInRange, Instant fromAt, Instant toAt) {
        Map<Reservation.Status, Long> counts = reports.countReservationsByStatus(fromAt, toAt).stream()
                .collect(Collectors.toMap(r -> (Reservation.Status) r[0], r -> (Long) r[1]));
        return new Operations(guests(paidInRange), counts.values().stream().mapToLong(Long::longValue).sum(),
                sum(counts, ARRIVED), sum(counts, CANCELLED_OR_NO_SHOW));
    }

    private static long sum(Map<Reservation.Status, Long> counts, Set<Reservation.Status> statuses) {
        return statuses.stream().map(counts::get).filter(Objects::nonNull).mapToLong(Long::longValue).sum();
    }
}
