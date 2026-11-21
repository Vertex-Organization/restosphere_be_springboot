package com.vertex.restosphere.service.impl;

import static com.vertex.restosphere.common.util.Values.*;

import com.vertex.restosphere.common.enums.*;
import com.vertex.restosphere.entity.*;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.mapper.ReservationMapper;
import com.vertex.restosphere.service.ReservationWorkflow;
import com.vertex.restosphere.service.ServiceWorkflow;
import com.vertex.restosphere.service.WorkspaceStore;
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
    static final Map<String, String> HISTORY_LABELS =
            Map.of(
                    "edit",
                    "Cập nhật lịch đặt",
                    "assign",
                    "Gán / đổi bàn",
                    "reservation.move",
                    "Quản lý đổi bàn",
                    "confirm",
                    "Xác nhận",
                    "reject",
                    "Từ chối",
                    "cancel",
                    "Hủy lịch",
                    "no-show",
                    "Không đến",
                    "check-in",
                    "Khách đã đến",
                    "handover",
                    "Bàn giao cho phục vụ");
    static final int EARLY_CHECK_IN_MINUTES = 60;
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
                db.all(Area.class).stream()
                        .map(a -> map("id", id(a), "name", a.getName()))
                        .toList());
    }

    @Override
    public Map<String, Object> createPublic(Map<String, Object> input) {
        var s = db.lock();
        if (!s.isOnlineReservations())
            throw ApiException.conflict("Nhà hàng tạm ngưng nhận đặt bàn trực tuyến.");
        var r = create(input, null, true, false);
        s.setRevision(s.getRevision() + 1);
        service.notify(
                Role.RECEPTIONIST,
                "NEW_WEB_RESERVATION",
                "Đặt bàn mới từ web",
                r.getCode() + " · " + r.getCustomerName(),
                id(r));
        mailer.send(
                r,
                "Đã nhận yêu cầu đặt bàn",
                "Nhà hàng đã nhận yêu cầu đặt bàn của bạn và sẽ xác nhận sớm.");
        db.flush();
        return publicView(r);
    }

    @Override
    public Reservation create(Map<String, Object> input, User actor, boolean web, boolean walkIn) {
        var r = new Reservation();
        r.setSource(
                web
                        ? Reservation.Source.WEB
                        : walkIn ? Reservation.Source.WALK_IN : Reservation.Source.PHONE);
        fill(r, input, web, walkIn);
        r.setCode(newCode(Instant.now()));
        db.save(r);
        history(
                r,
                actor,
                "CREATED",
                walkIn ? "Khách trực tiếp đã đến" : web ? "Khách đặt bàn qua web" : "Tạo lịch đặt");
        String tableId = text(input, "tableId");
        if (walkIn && tableId.isEmpty())
            throw invalid("tableId", "Chọn bàn trống cho khách trực tiếp.");
        if (!web && !tableId.isEmpty()) assign(r, tableId);
        if (walkIn) {
            var table = assignment(r).getTable();
            if (table.getStatus() != TableStatus.AVAILABLE || hasOpenSession(table))
                throw ApiException.conflict("Bàn đang có khách hoặc chưa sẵn sàng.");
            r.setStatus(Reservation.Status.ARRIVED);
            r.setActualGuests(r.getGuestCount());
            r.setArrivedAt(Instant.now());
        }
        replaceItems(r, input, web);
        return r;
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
        String areaId = required(input, "areaId", 100);
        r.setArea(db.get(Area.class, areaId));
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
        int capacity =
                db
                        .query(
                                RestaurantTable.class,
                                "from RestaurantTable t where t.area=?1 and t.status<>?2",
                                r.getArea(),
                                TableStatus.OUT_OF_SERVICE)
                        .stream()
                        .mapToInt(RestaurantTable::getCapacity)
                        .sum();
        // Sums guests per area; does not check how they fit into individual tables.
        long buffer = db.lock().getBufferMinutes() * 60L;
        int booked =
                db
                        .query(
                                Reservation.class,
                                "from Reservation o where o.area=?1 and o.status in ?2 and"
                                        + " o.startsAt<?3 and o.endsAt>?4",
                                r.getArea(),
                                ACTIVE,
                                r.getEndsAt().plusSeconds(buffer),
                                r.getStartsAt().minusSeconds(buffer))
                        .stream()
                        .filter(o -> !o.equals(r))
                        .mapToInt(Reservation::getGuestCount)
                        .sum();
        if (capacity - booked < r.getGuestCount())
            throw ApiException.conflict(
                    "Khu vực đã kín chỗ trong khung giờ này. Hãy chọn giờ hoặc khu khác.");
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
        return db
                .query(
                        Reservation.class,
                        "from Reservation r where upper(r.code)=?1 and r.phone=?2",
                        code.toUpperCase(),
                        phone)
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
        if (!late.isEmpty() || released > 0) s.setRevision(s.getRevision() + 1);
        return late.size() + released;
    }

    @Override
    public void assign(Reservation r, String tableId) {
        var table = db.get(RestaurantTable.class, tableId);
        int guests = r.getActualGuests() == null ? r.getGuestCount() : r.getActualGuests();
        if (!id(table.getArea()).equals(id(r.getArea()))
                || table.getCapacity() < guests
                || table.getStatus() == TableStatus.OUT_OF_SERVICE)
            throw ApiException.conflict(
                    "Bàn không phù hợp: khác khu vực, không đủ chỗ hoặc đang tạm ngưng.");
        ensureFree(r, table, r.getStartsAt(), r.getEndsAt());
        if (r.getId() != null)
            db.query(ReservationTable.class, "from ReservationTable a where a.reservation=?1", r)
                    .forEach(db::remove);
        db.flush();
        var a = new ReservationTable();
        a.setReservation(r);
        a.setTable(table);
        db.save(a);
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
                db.query(
                        RestaurantTable.class,
                        "from RestaurantTable t where t.area=?1 and t.capacity>=?2 order by"
                                + " t.capacity",
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
    public void command(String type, Map<String, Object> c, User actor) {
        var r = db.get(Reservation.class, required(c, "id", 100));
        switch (type) {
            case "edit" -> {
                require(r, Reservation.Status.PENDING, Reservation.Status.CONFIRMED);
                var input = object(c, "input");
                fill(r, input, false, false);
                replaceItems(r, input, false);
                String tableId = text(input, "tableId");
                if (!tableId.isEmpty()) assign(r, tableId);
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
                String tableId = required(c, "tableId", 100);
                if (r.getStatus() == Reservation.Status.SEATED) {
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
                } else assign(r, tableId);
            }
            case "confirm" -> {
                require(r, Reservation.Status.PENDING);
                if (assignment(r) == null) autoAssign(r);
                r.setStatus(Reservation.Status.CONFIRMED);
                mailer.send(
                        r,
                        "Đặt bàn đã được xác nhận",
                        "Lịch đặt bàn của bạn đã được xác nhận. Hẹn gặp bạn!");
            }
            case "reject" -> {
                require(r, Reservation.Status.PENDING);
                required(c, "reason", 1000);
                r.setStatus(Reservation.Status.REJECTED);
                mailer.send(
                        r,
                        "Đặt bàn không thành công",
                        "Rất tiếc, nhà hàng không thể nhận lịch đặt bàn này. Lý do: "
                                + text(c, "reason"));
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
                        "Lịch đặt bàn của bạn đã được hủy. Lý do: " + text(c, "reason"));
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
            case "handover" -> {
                require(r, Reservation.Status.ARRIVED);
                var a = assignment(r);
                if (a == null || a.getTable().getCapacity() < r.getActualGuests())
                    throw ApiException.conflict(
                            "Bàn hiện tại không đủ chỗ cho số khách thực tế. Hãy đổi bàn trước.");
                if (a.getTable().getStatus() != TableStatus.AVAILABLE)
                    throw ApiException.conflict("Bàn đang có khách hoặc chưa sẵn sàng.");
                if (hasOpenSession(a.getTable()))
                    throw ApiException.conflict("Bàn đang có khách khác sử dụng.");
                var now = Instant.now();
                ensureFree(
                        r,
                        a.getTable(),
                        now,
                        now.plus(Duration.between(r.getStartsAt(), r.getEndsAt())));
                var session = new TableSession();
                session.setTable(a.getTable());
                session.setReservation(r);
                session.setGuestCount(
                        r.getActualGuests() == null ? r.getGuestCount() : r.getActualGuests());
                session.setStartedAt(Instant.now());
                db.save(session);
                var bill = new Bill();
                bill.setSession(session);
                bill.setCode(code("B", session));
                db.save(bill);
                a.getTable().setStatus(TableStatus.OCCUPIED);
                r.setHandedOverAt(Instant.now());
                r.setStatus(Reservation.Status.SEATED);
                service.notify(
                        Role.WAITER,
                        "HANDOVER",
                        "Bàn " + a.getTable().getCode() + " chờ tiếp nhận",
                        r.getCode() + " · " + r.getActualGuests() + " khách",
                        id(session));
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

    void require(Reservation r, Reservation.Status... statuses) {
        if (!Arrays.asList(statuses).contains(r.getStatus()))
            throw ApiException.conflict(
                    "Không thể thực hiện thao tác này với lịch đang ở trạng thái hiện tại.");
    }

    @Override
    public ReservationTable assignment(Reservation r) {
        return r.getId() == null
                ? null
                : db
                        .query(
                                ReservationTable.class,
                                "from ReservationTable a where a.reservation=?1",
                                r)
                        .stream()
                        .findFirst()
                        .orElse(null);
    }

    @Override
    public void history(Reservation r, User actor, String action, String description) {
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
        return mapper.toPublicMap(r, items, ZoneId.of(db.lock().getTimezone()));
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
