package com.vertex.restosphere.service.impl;

import static com.vertex.restosphere.common.util.Values.*;

import com.vertex.restosphere.common.enums.*;
import com.vertex.restosphere.entity.*;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.mapper.ReservationMapper;
import com.vertex.restosphere.service.BillingWorkflow;
import com.vertex.restosphere.service.PayosPayments;
import com.vertex.restosphere.service.ReservationWorkflow;
import com.vertex.restosphere.service.ServiceWorkflow;
import com.vertex.restosphere.service.WorkspaceStore;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ReservationWorkflowImpl implements ReservationWorkflow {
    private final WorkspaceStore db;
    private final ServiceWorkflow service;
    private final ReservationMailer mailer;
    private final ReservationMapper mapper;
    private final BillingWorkflow billing;
    private final PayosLinks links;
    // Lazy: PayosPayments confirms reservations through this class, so a direct reference loops.
    private final org.springframework.beans.factory.ObjectProvider<PayosPayments> payosPayments;
    static final Map<String, String> HISTORY_LABELS =
            Map.ofEntries(
                    Map.entry("edit", "Cập nhật lịch đặt"),
                    Map.entry("assign", "Gán / đổi bàn"),
                    Map.entry("reservation.move", "Quản lý đổi bàn"),
                    Map.entry("confirm", "Xác nhận"),
                    Map.entry("reject", "Từ chối"),
                    Map.entry("cancel", "Hủy lịch"),
                    Map.entry("no-show", "Không đến"),
                    Map.entry("check-in", "Khách đã đến"),
                    Map.entry("handover", "Bàn giao cho phục vụ"),
                    Map.entry("deposit-link", "Tạo link thu cọc payOS"),
                    Map.entry("deposit-cash", "Chuyển thu ngân thu cọc tiền mặt"));
    static final int EARLY_CHECK_IN_MINUTES = 60;
    static final BigDecimal DEPOSIT_RATE = new BigDecimal("0.5");
    static final int DEPOSIT_WINDOW_MINUTES = 15;
    static final java.util.regex.Pattern HOURS =
            java.util.regex.Pattern.compile(
                    "(\\d{1,2})[:h](\\d{2})\\s*[-–]\\s*(\\d{1,2})[:h](\\d{2})");

    // Parses "10:00 - 22:00" from the admin opening-hours text; null = no limit. Overnight close
    // gets +1440.
    public static int[] openingMinutes(String text) {
        var m = HOURS.matcher(text == null ? "" : text);
        if (!m.find()) return null;
        int open = Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2)),
                close = Integer.parseInt(m.group(3)) * 60 + Integer.parseInt(m.group(4));
        if (open >= 1440 || close > 1440) return null;
        return new int[] {open, close <= open ? close + 1440 : close};
    }

    @Override
    public Map<String, Object> settings() {
        var s = db.lock();
        var hours = openingMinutes(s.getOpeningHours());
        return map(
                "openMinutes",
                hours == null ? null : hours[0],
                "closeMinutes",
                hours == null ? null : hours[1],
                "earlyCheckInMinutes",
                EARLY_CHECK_IN_MINUTES,
                "onlineReservations",
                s.isOnlineReservations(),
                "bufferMinutes",
                s.getBufferMinutes(),
                "noShowMinutes",
                s.getNoShowMinutes(),
                "tableHoldMinutes",
                s.getHoldMinutes(),
                "timezone",
                s.getTimezone(),
                "areas",
                db
                        .query(
                                Area.class,
                                "from Area a where a.active = true order by a.sortOrder, a.name")
                        .stream()
                        .map(a -> map("id", id(a), "name", a.getName()))
                        .toList());
    }

    @Override
    public Map<String, Object> createPublic(Map<String, Object> input) {
        var s = db.lock();
        if (!s.isOnlineReservations())
            throw ApiException.conflict("Nhà hàng tạm ngưng nhận đặt bàn trực tuyến.");
        var r = create(input, null, true, false);
        openOnlineDepositLink(r, s);
        s.setRevision(s.getRevision() + 1);
        service.notify(
                Role.RECEPTIONIST,
                "NEW_WEB_RESERVATION",
                "Đặt bàn mới từ web",
                r.getCode() + " · " + r.getCustomerName(),
                id(r));
        db.flush();
        return publicView(r);
    }

    @Override
    public Reservation create(
            Map<String, Object> input, Employee actor, boolean web, boolean walkIn) {
        var r = new Reservation();
        // TODO(confirm): a counter booking (guest at the desk, visit on another day) is source
        // WALK_IN but stays PENDING; input.counter marks it, while walkIn means seated right now.
        boolean counter = !web && Boolean.TRUE.equals(input.get("counter"));
        r.setSource(
                web
                        ? Reservation.Source.WEB
                        : walkIn || counter
                                ? Reservation.Source.WALK_IN
                                : Reservation.Source.PHONE);
        fill(r, input, web, walkIn);
        r.setCode(newCode(Instant.now()));
        db.save(r);
        history(
                r,
                actor,
                "CREATED",
                walkIn ? "Khách trực tiếp đã đến" : web ? "Khách đặt bàn qua web" : "Tạo lịch đặt");
        var tableIds = tableIds(input);
        if (walkIn && tableIds.isEmpty())
            throw invalid("tableId", "Chọn bàn trống cho khách trực tiếp.");
        if (!web && !tableIds.isEmpty()) assign(r, tableIds);
        if (walkIn) {
            for (var a : assignments(r)) {
                if (a.getTable().getStatus() != TableStatus.AVAILABLE
                        || hasOpenSession(a.getTable()))
                    throw ApiException.conflict("Bàn đang có khách hoặc chưa sẵn sàng.");
            }
            r.setStatus(Reservation.Status.ARRIVED);
            r.setActualGuests(r.getGuestCount());
            r.setArrivedAt(Instant.now());
        }
        replaceItems(r, input, web);
        if (!walkIn) {
            requireDepositForPreorder(r);
            // Phone bookings get the link right away to be sent to the guest; counter ones on
            // demand.
            if (!web && !Boolean.TRUE.equals(input.get("counter"))) tryOpenDepositLink(r, actor);
        }
        return r;
    }

    // Online deposits go through payOS only: if it is down the booking is refused, not left unpaid.
    private void openOnlineDepositLink(Reservation r, RestaurantSettings settings) {
        if (r.getDepositAmount() == null) return;
        try {
            links.ensureDepositLink(r, null);
        } catch (ApiException e) {
            String hotline = settings.getPhone() == null ? "nhà hàng" : settings.getPhone();
            throw new ApiException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "Hiện chưa thanh toán cọc online được. Vui lòng gọi "
                            + hotline
                            + " để đặt bàn.");
        }
    }

    // A staff-made booking is kept even if payOS is down: cash at the cashier is still possible.
    private void tryOpenDepositLink(Reservation r, Employee actor) {
        if (r.getDepositAmount() == null) return;
        try {
            links.ensureDepositLink(r, actor);
        } catch (ApiException e) {
            history(r, actor, "deposit-link-failed", "Chưa tạo được link thu cọc payOS.");
        }
    }

    void requireDepositForPreorder(Reservation r) {
        var subtotal =
                db
                        .query(
                                ReservationItem.class,
                                "from ReservationItem i where i.reservation=?1",
                                r)
                        .stream()
                        .map(i -> i.getUnitPrice().multiply(i.getQuantity()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (subtotal.signum() <= 0) return;
        r.setDepositAmount(whole(subtotal.multiply(DEPOSIT_RATE)));
        r.setDepositDueAt(Instant.now().plus(Duration.ofMinutes(DEPOSIT_WINDOW_MINUTES)));
    }

    // RS-ddMMyy-NNN: booking creation date (restaurant timezone) + sequence; never changes, even if
    // the booking is moved.
    String newCode(Instant createdAt) {
        String prefix =
                "RS-"
                        + createdAt
                                .atZone(ZoneId.of(db.lock().getTimezone()))
                                .format(java.time.format.DateTimeFormatter.ofPattern("ddMMyy"))
                        + "-";
        int next =
                db
                                .query(
                                        String.class,
                                        "select r.code from Reservation r where r.code like ?1",
                                        prefix + "%")
                                .stream()
                                .mapToInt(
                                        c -> {
                                            try {
                                                return Integer.parseInt(
                                                        c.substring(prefix.length()));
                                            } catch (NumberFormatException e) {
                                                return 0;
                                            }
                                        })
                                .max()
                                .orElse(0)
                        + 1;
        return prefix + String.format("%03d", next);
    }

    void fill(Reservation r, Map<String, Object> input, boolean web, boolean walkIn) {
        var customer = web ? input : object(input, "customer");
        String name = text(customer, "name");
        if (name.isEmpty() && walkIn) name = "Khách trực tiếp";
        if (name.isEmpty() || name.length() > 150)
            throw invalid("name", "Vui lòng nhập tên khách (tối đa 150 ký tự).");
        r.setCustomerName(name);
        String phone = phone(customer);
        if (!phone.matches("\\+?[0-9]{8,15}"))
            throw invalid("phone", "Số điện thoại không hợp lệ.");
        r.setPhone(phone);
        String email = text(input, "email");
        if (!email.isEmpty()
                && (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || email.length() > 254))
            throw invalid("email", "Email không hợp lệ.");
        r.setEmail(email);
        r.setGuestCount(integer(input, "guests", 1, 200));
        r.setArea(requestedArea(input, r.getArea()));
        Instant start;
        String startField = text(input, "startsAt").isEmpty() ? "date" : "startsAt";
        try {
            if (startField.equals("date")) {
                String date = required(input, "date", 10);
                String time = required(input, "time", 5);
                LocalDate.parse(date);
                startField = "time";
                start =
                        LocalDateTime.parse(date + "T" + time)
                                .atZone(ZoneId.of(db.lock().getTimezone()))
                                .toInstant();
            } else {
                start = Instant.parse(text(input, "startsAt"));
            }
        } catch (DateTimeException e) {
            throw invalid(startField, "Ngày giờ đặt bàn không hợp lệ.");
        }
        boolean moved = r.getStartsAt() == null || !start.equals(r.getStartsAt());
        if (moved && start.isBefore(Instant.now().minusSeconds(walkIn ? 1800 : 60))
                || start.isAfter(Instant.now().plus(Duration.ofDays(365))))
            throw invalid(
                    text(input, "startsAt").isEmpty() ? "date" : "startsAt",
                    "Không đặt bàn vào thời điểm đã qua hoặc quá 365 ngày tới.");
        int duration =
                input.containsKey(web ? "durationMinutes" : "duration")
                        ? integer(input, web ? "durationMinutes" : "duration", 15, 720)
                        : 120;
        var hours = openingMinutes(db.lock().getOpeningHours());
        if (hours != null) {
            var local = start.atZone(ZoneId.of(db.lock().getTimezone())).toLocalTime();
            int m = local.getHour() * 60 + local.getMinute();
            if (m < hours[0] && hours[1] > 1440) m += 1440;
            if (m < hours[0] || (walkIn ? m + 1 : m + duration) > hours[1])
                throw invalid(
                        startField,
                        "Giờ đến và thời lượng phải nằm trong giờ mở cửa ("
                                + db.lock().getOpeningHours()
                                + ").");
        }
        r.setStartsAt(start);
        r.setEndsAt(start.plusSeconds(duration * 60L));
        String note = text(input, web ? "note" : "notes");
        if (note.length() > 1000)
            throw invalid(web ? "note" : "notes", "Ghi chú tối đa 1000 ký tự.");
        r.setNote(note);
        String occasion = text(input, "occasion"), special = text(input, "specialRequests");
        if (occasion.length() > 150 || special.length() > 255)
            throw invalid(
                    occasion.length() > 150 ? "occasion" : "specialRequests",
                    "Dịp đặc biệt tối đa 150 ký tự, yêu cầu đặc biệt tối đa 255 ký tự.");
        r.setOccasion(occasion);
        r.setSpecialRequests(special);
        int capacity = areaCapacity(r.getArea());
        // Sums guests per area; does not check how they fit into individual tables.
        long buffer = db.lock().getBufferMinutes() * 60L;
        int booked =
                db
                        .query(
                                Reservation.class,
                                "from Reservation o where o.status in ?1 and o.startsAt<?2 and"
                                        + " o.endsAt>?3",
                                ACTIVE,
                                r.getEndsAt().plusSeconds(buffer),
                                r.getStartsAt().minusSeconds(buffer))
                        .stream()
                        .filter(o -> !o.equals(r))
                        .filter(o -> r.getArea() == null || bookedIn(o, r.getArea()))
                        .mapToInt(Reservation::getGuestCount)
                        .sum();
        if (capacity - booked < r.getGuestCount())
            throw ApiException.conflict(
                    "Khu vực đã kín chỗ trong khung giờ này. Hãy chọn giờ hoặc khu khác.");
    }

    // Empty areaId means "no preference". An inactive area is only accepted when unchanged.
    private Area requestedArea(Map<String, Object> input, Area current) {
        String areaId = text(input, "areaId");
        if (areaId.isEmpty()) return null;
        var area = db.get(Area.class, areaId);
        if (!area.isActive() && (current == null || !current.getId().equals(area.getId())))
            throw invalid("areaId", "Khu vực này tạm đóng, vui lòng chọn khu khác.");
        return area;
    }

    private int areaCapacity(Area area) {
        var tables =
                area == null
                        ? db.query(
                                RestaurantTable.class,
                                "from RestaurantTable t where t.area.active = true and"
                                        + " t.status<>?1",
                                TableStatus.OUT_OF_SERVICE)
                        : db.query(
                                RestaurantTable.class,
                                "from RestaurantTable t where t.area=?1 and t.status<>?2",
                                area,
                                TableStatus.OUT_OF_SERVICE);
        return tables.stream().mapToInt(RestaurantTable::getCapacity).sum();
    }

    // A booking without a preferred area counts against the area of its assigned table.
    private boolean bookedIn(Reservation other, Area area) {
        if (other.getArea() != null) return other.getArea().getId().equals(area.getId());
        var assigned = assignment(other);
        return assigned != null && assigned.getTable().getArea().getId().equals(area.getId());
    }

    void replaceItems(Reservation r, Map<String, Object> input, boolean web) {
        if (r.getId() != null)
            db.query(ReservationItem.class, "from ReservationItem i where i.reservation=?1", r)
                    .forEach(db::remove);
        String field = web ? "items" : "preorder";
        try {
            for (var line : lines(input, field)) {
                var menu =
                        db.get(MenuItem.class, required(line, web ? "dishId" : "menuItemId", 100));
                if (menu.getStatus() != MenuItemStatus.AVAILABLE || !menu.getCategory().isVisible())
                    throw ApiException.conflict("Có món đặt trước đã hết hoặc ngừng bán.");
                var quantity = number(line, "quantity");
                validateQuantity(menu, quantity);
                var item = new ReservationItem();
                item.setReservation(r);
                item.setMenuItem(menu);
                item.setQuantity(quantity);
                item.setUnitPrice(menu.getPrice());
                if (text(line, "note").length() > 1000) throw invalid("Ghi chú tối đa 1000 ký tự.");
                item.setNote(text(line, "note"));
                db.save(item);
            }
        } catch (ApiException e) {
            if (e.getStatus() == org.springframework.http.HttpStatus.BAD_REQUEST) {
                throw invalid(field, e.getMessage());
            }
            throw e;
        }
    }

    @Override
    public Map<String, Object> lookup(Map<String, Object> input) {
        return publicView(find(input));
    }

    @Override
    public Map<String, Object> cancelPublic(Map<String, Object> input) {
        var r = find(input);
        command("cancel", map("id", id(r), "reason", required(input, "reason", 1000)), null);
        var s = db.lock();
        s.setRevision(s.getRevision() + 1);
        db.flush();
        return publicView(r);
    }

    Reservation find(Map<String, Object> input) {
        db.lock();
        String code = required(input, "code", 30), phone = phone(input);
        var phones = phoneVariants(phone);
        return db
                .query(
                        Reservation.class,
                        "from Reservation r where upper(r.code)=?1 and r.phone in ?2",
                        code.toUpperCase(),
                        phones)
                .stream()
                .findFirst()
                .orElseThrow(
                        () ->
                                new ApiException(
                                        org.springframework.http.HttpStatus.NOT_FOUND,
                                        "Không tìm thấy lịch đặt. Kiểm tra lại mã và số điện"
                                                + " thoại."));
    }

    @Override
    public int expireNoShows() {
        var s = db.lock();
        var now = Instant.now();
        var late =
                db.query(
                        Reservation.class,
                        "from Reservation r where r.status=?1 and r.startsAt<=?2",
                        Reservation.Status.CONFIRMED,
                        now.minusSeconds(s.getNoShowMinutes() * 60L));
        for (var r : late) {
            command("no-show", map("id", id(r)), null);
            service.notify(
                    Role.RECEPTIONIST,
                    "AUTO_NO_SHOW",
                    "Tự động đánh dấu không đến",
                    r.getCode() + " · " + r.getCustomerName(),
                    id(r));
        }
        // Hold window: once a confirmed guest is later than tableHoldMinutes, the table is released
        // for others; the booking keeps waiting until no-show.
        int released = 0;
        if (s.getHoldMinutes() < s.getNoShowMinutes()) {
            for (var a :
                    db.query(
                            ReservationTable.class,
                            "from ReservationTable a where a.reservation.status=?1 and"
                                    + " a.reservation.startsAt<=?2",
                            Reservation.Status.CONFIRMED,
                            now.minusSeconds(s.getHoldMinutes() * 60L))) {
                var r = a.getReservation();
                db.remove(a);
                released++;
                history(
                        r,
                        null,
                        "hold-expired",
                        "Khách trễ quá "
                                + s.getHoldMinutes()
                                + " phút, đã trả bàn "
                                + a.getTable().getCode()
                                + ". Gán lại bàn khi khách đến.");
                service.notify(
                        Role.RECEPTIONIST,
                        "HOLD_EXPIRED",
                        "Đã trả bàn " + a.getTable().getCode(),
                        r.getCode()
                                + " · "
                                + r.getCustomerName()
                                + " trễ quá "
                                + s.getHoldMinutes()
                                + " phút",
                        id(r));
            }
        }
        int unpaid = cancelUnpaidDeposits(now);
        if (!late.isEmpty() || released > 0 || unpaid > 0) s.setRevision(s.getRevision() + 1);
        return late.size() + released + unpaid;
    }

    @Override
    public boolean confirmAfterDeposit(Reservation r) {
        if (r.getStatus() != Reservation.Status.PENDING || r.awaitingDeposit()) return false;
        if (assignment(r) == null) {
            try {
                autoAssign(r);
            } catch (ApiException e) {
                service.notify(
                        Role.RECEPTIONIST,
                        "DEPOSIT_PAID_NO_TABLE",
                        "Đã nhận cọc nhưng chưa xếp được bàn",
                        r.getCode() + " · " + r.getCustomerName(),
                        id(r));
                return false;
            }
        }
        command("confirm", map("id", id(r)), null);
        return true;
    }

    // Policy: a paid deposit is never refunded, whether the booking is cancelled or a no-show.
    static String depositNote(Reservation r) {
        if (!paidDeposit(r)) return "";
        return " Theo chính sách đặt cọc, tiền cọc "
                + ReservationMailer.money(r.getDepositAmount())
                + " không được hoàn lại.";
    }

    // The deposit rides on the first table's bill; later tables of the same booking stay clear.
    void attachDepositToBill(Reservation r, Bill bill) {
        for (var p : r.getPayments()) {
            if (p.getBill() == null && p.getStatus() == Payment.Status.COMPLETED) p.setBill(bill);
        }
    }

    static boolean paidDeposit(Reservation r) {
        return r.getDepositAmount() != null && !r.awaitingDeposit();
    }

    int cancelUnpaidDeposits(Instant now) {
        var expired =
                db.query(
                        Reservation.class,
                        "from Reservation r where r.status=?1 and r.depositAmount is not null"
                                + " and r.depositDueAt<=?2 and not exists (select 1 from Payment p"
                                + " where p.reservation=r and p.status=?3)",
                        Reservation.Status.PENDING,
                        now,
                        Payment.Status.COMPLETED);
        int cancelled = 0;
        for (var r : expired) {
            if (paidAccordingToPayos(r)) continue;
            // Cancel the link first so the guest cannot pay a booking that is already gone.
            links.cancelDepositLink(r, "Quá hạn thu cọc");
            command(
                    "cancel",
                    map(
                            "id",
                            id(r),
                            "reason",
                            "Quá hạn chuyển tiền cọc " + DEPOSIT_WINDOW_MINUTES + " phút"),
                    null);
            service.notify(
                    Role.RECEPTIONIST,
                    "DEPOSIT_EXPIRED",
                    "Hủy lịch do chưa nhận cọc",
                    r.getCode() + " · " + r.getCustomerName(),
                    id(r));
            cancelled++;
        }
        return cancelled;
    }

    // The webhook may have been lost: ask payOS before cancelling, and if it was paid, confirm.
    // If payOS cannot be reached the booking is kept for the next run rather than cancelled blind.
    private boolean paidAccordingToPayos(Reservation r) {
        for (var p : List.copyOf(r.getPayments())) {
            if (p.getMethod() != Payment.Method.PAYOS || p.getStatus() != Payment.Status.PENDING)
                continue;
            try {
                var outcome = payosPayments.getObject().refresh(p);
                if (outcome == PayosPayments.Outcome.COMPLETED
                        || outcome == PayosPayments.Outcome.ALREADY_COMPLETED) return true;
            } catch (ApiException e) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void assign(Reservation r, String tableId) {
        assign(r, List.of(tableId));
    }

    @Override
    public void assign(Reservation r, List<String> tableIds) {
        var tables =
                tableIds.stream()
                        .distinct()
                        .map(tableId -> db.get(RestaurantTable.class, tableId))
                        .toList();
        int guests = r.getActualGuests() == null ? r.getGuestCount() : r.getActualGuests();
        for (var table : tables) {
            if ((r.getArea() != null && !id(table.getArea()).equals(id(r.getArea())))
                    || !table.getArea().isActive()
                    || table.getStatus() == TableStatus.OUT_OF_SERVICE)
                throw ApiException.conflict(
                        "Bàn không phù hợp: khác khu vực, không đủ chỗ hoặc đang tạm ngưng.");
        }
        if (!fits(tables, guests))
            throw ApiException.conflict(
                    "Bàn không phù hợp: khác khu vực, không đủ chỗ hoặc đang tạm ngưng.");
        for (var table : tables) ensureFree(r, table, r.getStartsAt(), r.getEndsAt());
        if (r.getId() != null)
            db.query(ReservationTable.class, "from ReservationTable a where a.reservation=?1", r)
                    .forEach(db::remove);
        db.flush();
        for (var table : tables) {
            var a = new ReservationTable();
            a.setReservation(r);
            a.setTable(table);
            db.save(a);
        }
    }

    // One table must seat everyone; several tables must together seat everyone, one guest each at
    // least.
    static boolean fits(List<RestaurantTable> tables, int guests) {
        int capacity = tables.stream().mapToInt(RestaurantTable::getCapacity).sum();
        return tables.size() == 1
                ? capacity >= guests
                : !tables.isEmpty() && capacity >= guests && guests >= tables.size();
    }

    void ensureFree(Reservation r, RestaurantTable table, Instant start, Instant end) {
        long buffer = db.lock().getBufferMinutes() * 60L;
        boolean clash =
                db
                        .query(
                                ReservationTable.class,
                                "from ReservationTable a where a.table=?1 and a.reservation.status"
                                        + " in ?2 and a.reservation.startsAt<?3 and"
                                        + " a.reservation.endsAt>?4",
                                table,
                                ACTIVE,
                                end.plusSeconds(buffer),
                                start.minusSeconds(buffer))
                        .stream()
                        .anyMatch(a -> !a.getReservation().equals(r));
        if (clash) throw ApiException.conflict("Bàn đã có lịch khác trùng khung giờ này.");
    }

    void autoAssign(Reservation r) {
        var tables =
                r.getArea() == null
                        ? db.query(
                                RestaurantTable.class,
                                "from RestaurantTable t where t.area.active = true and"
                                        + " t.capacity>=?1 order by t.capacity",
                                r.getGuestCount())
                        : db.query(
                                RestaurantTable.class,
                                "from RestaurantTable t where t.area=?1 and t.capacity>=?2 order"
                                        + " by t.capacity",
                                r.getArea(),
                                r.getGuestCount());
        for (var t : tables) {
            try {
                assign(r, id(t));
                return;
            } catch (ApiException ignored) {
            }
        }
        throw ApiException.conflict("Không còn bàn trống đủ chỗ trong khung giờ này.");
    }

    static final List<Reservation.Status> ACTIVE =
            List.of(
                    Reservation.Status.PENDING,
                    Reservation.Status.CONFIRMED,
                    Reservation.Status.ARRIVED,
                    Reservation.Status.SEATED);

    @Override
    public boolean active(Reservation r) {
        return ACTIVE.contains(r.getStatus());
    }

    @Override
    public void command(String type, Map<String, Object> c, Employee actor) {
        var r = db.get(Reservation.class, required(c, "id", 100));
        switch (type) {
            case "edit" -> {
                require(r, Reservation.Status.PENDING, Reservation.Status.CONFIRMED);
                var input = object(c, "input");
                fill(r, input, false, false);
                replaceItems(r, input, false);
                var tableIds = tableIds(input);
                if (!tableIds.isEmpty()) assign(r, tableIds);
                else if (r.getStatus() == Reservation.Status.CONFIRMED)
                    throw ApiException.conflict("Lịch đã xác nhận phải giữ một bàn. Hãy chọn bàn.");
                else
                    db.query(
                                    ReservationTable.class,
                                    "from ReservationTable a where a.reservation=?1",
                                    r)
                            .forEach(db::remove);
            }
            case "assign", "reservation.move" -> {
                require(
                        r,
                        Reservation.Status.PENDING,
                        Reservation.Status.CONFIRMED,
                        Reservation.Status.ARRIVED,
                        Reservation.Status.SEATED);
                var previous = assignment(r);
                var tableIds = tableIds(c);
                if (tableIds.isEmpty()) throw invalid("tableId", "Chọn bàn cho lịch đặt.");
                if (r.getStatus() == Reservation.Status.SEATED) {
                    String tableId = tableIds.getFirst();
                    if (tableIds.size() > 1 || assignments(r).size() > 1)
                        throw ApiException.conflict(
                                "Lịch có nhiều bàn đã nhận khách, hãy ghép hoặc tách phiên thay vì"
                                        + " đổi bàn.");
                    var session =
                            db
                                    .query(
                                            TableSession.class,
                                            "from TableSession s where s.reservation=?1 and"
                                                    + " s.status<>?2",
                                            r,
                                            TableSession.Status.CLOSED)
                                    .stream()
                                    .findFirst()
                                    .orElseThrow(
                                            () ->
                                                    ApiException.conflict(
                                                            "Không tìm thấy lượt phục vụ đang"
                                                                    + " mở."));
                    var table = db.get(RestaurantTable.class, tableId);
                    if (table.getStatus() != TableStatus.AVAILABLE || hasOpenSession(table))
                        throw ApiException.conflict("Bàn đang có khách hoặc chưa sẵn sàng.");
                    assign(r, tableId);
                    session.getTable().setStatus(TableStatus.AVAILABLE);
                    session.setTable(table);
                    table.setStatus(TableStatus.OCCUPIED);
                    service.notify(
                            Role.WAITER,
                            "TABLE_REASSIGNED",
                            "Đổi bàn " + previous.getTable().getCode() + " → " + table.getCode(),
                            r.getCode() + " · " + r.getCustomerName(),
                            id(session));
                } else assign(r, tableIds);
            }
            case "confirm" -> {
                require(r, Reservation.Status.PENDING);
                if (r.awaitingDeposit())
                    throw ApiException.conflict("Khách chưa chuyển tiền cọc, chưa thể xác nhận.");
                if (assignment(r) == null) autoAssign(r);
                r.setStatus(Reservation.Status.CONFIRMED);
                mailer.send(
                        r,
                        "Đã tiếp nhận yêu cầu đặt bàn",
                        "Nhà hàng đã tiếp nhận yêu cầu đặt bàn của bạn. Hẹn gặp bạn!");
            }
            case "reject" -> {
                require(r, Reservation.Status.PENDING);
                required(c, "reason", 1000);
                r.setStatus(Reservation.Status.REJECTED);
                mailer.send(
                        r,
                        "Đặt bàn không thành công",
                        "Rất tiếc, nhà hàng không thể nhận lịch đặt bàn này. Lý do: "
                                + text(c, "reason")
                                + depositNote(r));
            }
            case "cancel" -> {
                require(r, Reservation.Status.PENDING, Reservation.Status.CONFIRMED);
                required(c, "reason", 1000);
                r.setStatus(Reservation.Status.CANCELLED);
                if (actor == null)
                    service.notify(
                            Role.RECEPTIONIST,
                            "RESERVATION_CANCELLED",
                            "Khách hủy đặt bàn",
                            r.getCode() + " · " + text(c, "reason"),
                            id(r));
                mailer.send(
                        r,
                        "Đặt bàn đã bị hủy",
                        "Lịch đặt bàn của bạn đã được hủy. Lý do: "
                                + text(c, "reason")
                                + depositNote(r));
                if (paidDeposit(r)) history(r, actor, "deposit", "Không hoàn cọc");
            }
            case "no-show" -> {
                require(r, Reservation.Status.CONFIRMED);
                if (Instant.now()
                        .isBefore(r.getStartsAt().plusSeconds(db.lock().getNoShowMinutes() * 60L)))
                    throw ApiException.conflict(
                            "Chưa hết thời gian chờ khách, chưa thể đánh dấu không đến.");
                r.setStatus(Reservation.Status.NO_SHOW);
            }
            case "check-in" -> {
                require(r, Reservation.Status.CONFIRMED);
                if (Instant.now()
                        .isBefore(r.getStartsAt().minusSeconds(EARLY_CHECK_IN_MINUTES * 60L)))
                    throw ApiException.conflict(
                            "Chỉ nhận khách sớm tối đa "
                                    + EARLY_CHECK_IN_MINUTES
                                    + " phút trước giờ hẹn.");
                r.setActualGuests(integer(c, "actualGuests", 1, 200));
                r.setArrivedAt(Instant.now());
                r.setStatus(Reservation.Status.ARRIVED);
            }
            case "handover" -> handover(r);
            case "deposit-link" -> {
                requireDepositDue(r);
                links.ensureDepositLink(r, actor);
            }
            case "deposit-cash" -> {
                requireDepositDue(r);
                // Cash must reach the cashier, never the front desk; the link is dropped so the
                // guest cannot also pay by transfer and pay twice.
                links.cancelDepositLink(r, "Chuyển thu ngân thu tiền mặt");
                r.setDepositCashRequestedAt(Instant.now());
            }
            default -> throw invalid("Thao tác không được hỗ trợ.");
        }
        String label = HISTORY_LABELS.getOrDefault(type, type);
        history(
                r,
                actor,
                type,
                text(c, "reason").isEmpty() ? label : label + ": " + text(c, "reason"));
    }

    // One session per table, all pointing at the reservation and sharing ONE bill; the deposit
    // rides on that bill. Guests are split by capacity, at least one per table.
    private void handover(Reservation r) {
        require(r, Reservation.Status.ARRIVED);
        var assigned = assignments(r);
        var tables = assigned.stream().map(ReservationTable::getTable).toList();
        int guests = r.getActualGuests() == null ? r.getGuestCount() : r.getActualGuests();
        if (tables.isEmpty() || !fits(tables, guests))
            throw ApiException.conflict(
                    "Bàn hiện tại không đủ chỗ cho số khách thực tế. Hãy đổi bàn trước.");
        var now = Instant.now();
        for (var table : tables) {
            if (table.getStatus() != TableStatus.AVAILABLE)
                throw ApiException.conflict("Bàn đang có khách hoặc chưa sẵn sàng.");
            if (hasOpenSession(table))
                throw ApiException.conflict("Bàn đang có khách khác sử dụng.");
            ensureFree(r, table, now, now.plus(Duration.between(r.getStartsAt(), r.getEndsAt())));
        }
        int remaining = guests;
        var bill = billing.open();
        for (int i = 0; i < tables.size(); i++) {
            var table = tables.get(i);
            int seated = Math.min(table.getCapacity(), remaining - (tables.size() - i - 1));
            remaining -= seated;
            var session = new TableSession();
            session.setBill(bill);
            bill.getSessions().add(session);
            session.setTable(table);
            session.setReservation(r);
            session.setGuestCount(seated);
            session.setStartedAt(now.plusMillis(i));
            db.save(session);
            table.setStatus(TableStatus.OCCUPIED);
            service.notify(
                    Role.WAITER,
                    "HANDOVER",
                    "Bàn " + table.getCode() + " chờ tiếp nhận",
                    r.getCode() + " · " + seated + " khách",
                    id(session));
        }
        attachDepositToBill(r, bill);
        r.setHandedOverAt(now);
        r.setStatus(Reservation.Status.SEATED);
    }

    private static List<String> tableIds(Map<String, Object> input) {
        var ids = new ArrayList<String>();
        if (input.get("tableIds") instanceof List<?> list) list.forEach(x -> ids.add(x.toString()));
        String one = text(input, "tableId");
        if (!one.isEmpty()) ids.add(one);
        return ids.stream().distinct().toList();
    }

    private void requireDepositDue(Reservation r) {
        require(r, Reservation.Status.PENDING);
        if (!r.awaitingDeposit()) throw ApiException.conflict("Đặt bàn này không cần thu cọc.");
    }

    void require(Reservation r, Reservation.Status... statuses) {
        if (!Arrays.asList(statuses).contains(r.getStatus()))
            throw ApiException.conflict(
                    "Không thể thực hiện thao tác này với lịch đang ở trạng thái hiện tại.");
    }

    @Override
    public ReservationTable assignment(Reservation r) {
        return assignments(r).stream().findFirst().orElse(null);
    }

    @Override
    public List<ReservationTable> assignments(Reservation r) {
        return r.getId() == null
                ? List.of()
                : db.query(
                        ReservationTable.class,
                        "from ReservationTable a where a.reservation=?1 order by a.createdAt,"
                                + " a.id",
                        r);
    }

    @Override
    public void history(Reservation r, Employee actor, String action, String description) {
        var h = new AuditLog();
        h.setTargetType("RESERVATION");
        h.setTargetId(r.getId());
        h.setActor(actor);
        h.setAction(action);
        h.setDescription(description);
        db.save(h);
    }

    @Override
    public Map<String, Object> publicView(Reservation r) {
        var items =
                db.query(ReservationItem.class, "from ReservationItem i where i.reservation=?1", r);
        var s = db.lock();
        return mapper.toPublicMap(r, items, ZoneId.of(s.getTimezone()));
    }

    /** Bàn đang có phiên phục vụ chưa đóng. */
    private boolean hasOpenSession(RestaurantTable table) {
        return !db.query(
                        TableSession.class,
                        "from TableSession s where s.table=?1 and s.status<>?2",
                        table,
                        TableSession.Status.CLOSED)
                .isEmpty();
    }
}
