package com.vertex.restosphere.service.impl;

import com.vertex.restosphere.common.enums.PriceUnit;
import com.vertex.restosphere.common.util.Values;
import com.vertex.restosphere.entity.Customer;
import com.vertex.restosphere.entity.Reservation;
import com.vertex.restosphere.entity.ReservationItem;
import com.vertex.restosphere.entity.RestaurantSettings;
import com.vertex.restosphere.service.WorkspaceStore;
import jakarta.mail.MessagingException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.util.HtmlUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationMailer {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String LOGO_CID = "restaurant-logo";
    private static final ClassPathResource DEFAULT_LOGO = new ClassPathResource("mail/logo.png");
    // Georgia lacks stacked Vietnamese diacritics (ầ, ặ, ệ); every fallback here covers them.
    private static final String SERIF = "'Playfair Display','Times New Roman',Times,serif";
    private static final String SANS = "'Helvetica Neue',Arial,sans-serif";

    private final ObjectProvider<JavaMailSender> sender;
    private final WorkspaceStore db;

    @Value("${app.mail.from:}")
    String from;

    record Content(String subject, String text, String html, boolean inlineLogo) {}

    public void send(Reservation r, String subject, String body) {
        var mail = sender.getIfAvailable();
        var s = db.lock();
        if (mail == null
                || !s.isEmailNotifications()
                || r.getEmail() == null
                || r.getEmail().isBlank()) return;
        var items =
                db.query(
                        ReservationItem.class,
                        "from ReservationItem i join fetch i.menuItem where i.reservation=?1",
                        r);
        deliver(mail, r.getEmail(), r.getCode(), render(r, s, subject, body, items));
    }

    /** Returns whether the email was queued; SMTP delivery itself happens in the background. */
    public boolean sendWelcome(Customer c) {
        var mail = sender.getIfAvailable();
        var s = db.lock();
        if (mail == null || !s.isEmailNotifications() || !notBlank(c.getEmail())) return false;
        deliver(mail, c.getEmail(), "member " + c.getId(), renderWelcome(c, s));
        return true;
    }

    static Content renderWelcome(Customer c, RestaurantSettings s) {
        var zone = ZoneId.of(s.getTimezone());
        var rows = new ArrayList<String[]>();
        rows.add(new String[] {"Số điện thoại", c.getPhone()});
        var joined = c.getCreatedAt() == null ? Instant.now() : c.getCreatedAt();
        rows.add(new String[] {"Ngày tham gia", joined.atZone(zone).format(DATE)});
        var contact = contact(s);
        boolean customLogo = notBlank(s.getLogoUrl());
        String logo = customLogo ? s.getLogoUrl() : "cid:" + LOGO_CID;
        String title = "Chào mừng thành viên mới";
        String body =
                "Chúc mừng quý khách đã trở thành thành viên của "
                        + s.getName()
                        + ". Cảm ơn quý khách đã đồng hành cùng nhà hàng, chúng tôi rất mong được"
                        + " đón tiếp quý khách trong những lần ghé thăm sắp tới.";
        return new Content(
                "[" + s.getName() + "] " + title,
                text(s.getName(), c.getFullName(), body, rows, contact),
                html(
                        logo,
                        s.getName(),
                        "Thư chào mừng",
                        title,
                        c.getFullName(),
                        body,
                        rows,
                        MEMBER_NOTE,
                        contact),
                !customLogo && DEFAULT_LOGO.exists());
    }

    private static final String RESERVATION_NOTE =
            "Vui lòng giữ mã đặt bàn để tra cứu hoặc hủy lịch trên trang web của nhà hàng. Rất hân"
                    + " hạnh được đón tiếp quý khách.";
    private static final String MEMBER_NOTE =
            "Khi đặt bàn, quý khách chỉ cần dùng số điện thoại này để nhà hàng nhận ra và phục vụ"
                    + " chu đáo hơn.";

    private static List<String> contact(RestaurantSettings s) {
        var contact = new ArrayList<String>();
        if (notBlank(s.getAddress())) contact.add(s.getAddress());
        if (notBlank(s.getPhone())) contact.add("Điện thoại: " + s.getPhone());
        return contact;
    }

    private void deliver(JavaMailSender mail, String to, String tag, Content content) {
        Runnable task =
                () -> {
                    try {
                        var message = mail.createMimeMessage();
                        var helper = new MimeMessageHelper(message, true, "UTF-8");
                        if (!from.isBlank()) helper.setFrom(from);
                        helper.setTo(to);
                        helper.setSubject(content.subject());
                        helper.setText(content.text(), content.html());
                        if (content.inlineLogo())
                            helper.addInline(LOGO_CID, DEFAULT_LOGO, "image/png");
                        mail.send(message);
                    } catch (MessagingException | MailException e) {
                        log.warn("Email for {} failed: {}", tag, e.getMessage());
                    }
                };
        // SMTP can take tens of seconds; never make the HTTP request wait for it.
        Runnable background = () -> CompletableFuture.runAsync(task);
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            background.run();
                        }
                    });
        else background.run();
    }

    static Content render(
            Reservation r,
            RestaurantSettings s,
            String subject,
            String body,
            List<ReservationItem> items) {
        var zone = ZoneId.of(s.getTimezone());
        var start = r.getStartsAt().atZone(zone);
        var rows = new ArrayList<String[]>();
        rows.add(new String[] {"Mã đặt bàn", r.getCode()});
        rows.add(new String[] {"Ngày", start.format(DATE)});
        String time = start.format(TIME);
        if (r.getEndsAt() != null) time += " – " + r.getEndsAt().atZone(zone).format(TIME);
        rows.add(new String[] {"Giờ", time});
        rows.add(new String[] {"Số khách", r.getGuestCount() + " khách"});
        if (r.getArea() != null) rows.add(new String[] {"Khu vực", r.getArea().getName()});
        if (r.getDepositAmount() != null && !r.awaitingDeposit())
            rows.add(new String[] {"Đã cọc", money(r.getDepositAmount())});
        var contact = contact(s);
        boolean customLogo = notBlank(s.getLogoUrl());
        String logo = customLogo ? s.getLogoUrl() : "cid:" + LOGO_CID;
        String fullSubject = "[" + s.getName() + "] " + subject + " - " + r.getCode();
        return new Content(
                fullSubject,
                text(s.getName(), r.getCustomerName(), body, rows, contact) + textMenu(items),
                html(
                                logo,
                                s.getName(),
                                "Thư xác nhận",
                                subject,
                                r.getCustomerName(),
                                body,
                                rows,
                                RESERVATION_NOTE,
                                contact)
                        .replace(MENU_SLOT, htmlMenu(items)),
                !customLogo && DEFAULT_LOGO.exists());
    }

    private static final String MENU_SLOT = "<!--preorder-->";

    static String quantity(ReservationItem i) {
        String q = i.getQuantity().stripTrailingZeros().toPlainString();
        return i.getMenuItem().getPriceUnit() == PriceUnit.KG ? q + " kg" : "× " + q;
    }

    static String money(BigDecimal value) {
        return String.format("%,d", Values.whole(value).longValue()).replace(',', '.') + "đ";
    }

    private static BigDecimal lineTotal(ReservationItem i) {
        return i.getUnitPrice().multiply(i.getQuantity());
    }

    private static String textMenu(List<ReservationItem> items) {
        if (items.isEmpty()) return "";
        var out = new StringBuilder("\n\nMón đặt trước:\n");
        var total = BigDecimal.ZERO;
        for (var i : items) {
            total = total.add(lineTotal(i));
            out.append("- ")
                    .append(i.getMenuItem().getName())
                    .append(' ')
                    .append(quantity(i))
                    .append(": ")
                    .append(money(lineTotal(i)))
                    .append('\n');
        }
        return out.append("Tạm tính: ").append(money(total)).toString();
    }

    private static String htmlMenu(List<ReservationItem> items) {
        if (items.isEmpty()) return "";
        var lines = new StringBuilder();
        var total = BigDecimal.ZERO;
        for (var i : items) {
            total = total.add(lineTotal(i));
            String image = i.getMenuItem().getImageUrl();
            String thumb =
                    notBlank(image)
                            ? "<img src=\"%s\" alt=\"\" width=\"64\" height=\"64\" style=\"display:block;width:64px;height:64px;object-fit:cover;border-radius:8px;border:1px solid #e8d5a8\">"
                                    .formatted(esc(image))
                            : "";
            lines.append(
                    """
                    <tr><td width="76" style="padding:10px 0;border-bottom:1px solid #f1e4c6">%s</td>\
                    <td style="padding:10px 0;border-bottom:1px solid #f1e4c6;font-family:%s;\
                    font-size:17px;color:#5c0000">%s<div style="font-family:%s;font-size:13px;\
                    color:#9a7b4f;margin-top:2px">%s</div></td>\
                    <td align="right" style="padding:10px 0;border-bottom:1px solid #f1e4c6;\
                    font-family:%s;font-size:15px;color:#3b2a1a;white-space:nowrap">%s</td></tr>
                    """
                            .formatted(
                                    thumb,
                                    SERIF,
                                    esc(i.getMenuItem().getName()),
                                    SANS,
                                    esc(quantity(i)),
                                    SANS,
                                    money(lineTotal(i))));
        }
        return """
                <tr><td style="padding:20px 40px 8px">
                <div style="font-family:%s;font-size:12px;letter-spacing:3px;text-transform:uppercase;\
                color:#9a7b4f;margin-bottom:6px">Món đặt trước</div>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" \
                style="border-top:1px solid #e8d5a8">%s
                <tr><td></td><td style="padding:12px 0;font-family:%s;font-size:13px;color:#9a7b4f">\
                Tạm tính</td><td align="right" style="padding:12px 0;font-family:%s;font-size:18px;\
                font-weight:700;color:#870000;white-space:nowrap">%s</td></tr>
                </table>
                <div style="font-family:%s;font-size:12px;color:#9a7b4f;margin-top:4px">Hải sản tính \
                theo kg được cân thực tế tại bàn; giá chưa gồm phí dịch vụ.</div>
                </td></tr>
                """
                .formatted(SANS, lines, SANS, SERIF, money(total), SANS);
    }

    private static String text(
            String restaurant,
            String customer,
            String body,
            List<String[]> rows,
            List<String> contact) {
        var out = new StringBuilder("Xin chào ").append(customer).append(",\n\n");
        out.append(body).append("\n\n");
        for (var row : rows) out.append(row[0]).append(": ").append(row[1]).append('\n');
        out.append('\n').append(restaurant);
        for (var line : contact) out.append('\n').append(line);
        return out.toString();
    }

    private static String html(
            String logo,
            String restaurant,
            String kicker,
            String title,
            String customer,
            String body,
            List<String[]> rows,
            String note,
            List<String> contact) {
        var details = new StringBuilder();
        for (var row : rows)
            details.append(
                    """
                    <tr><td style="padding:12px 0;border-bottom:1px solid #f1e4c6;\
                    font-family:%s;font-size:12px;letter-spacing:1.5px;text-transform:uppercase;\
                    color:#9a7b4f;width:130px">%s</td>\
                    <td style="padding:12px 0;border-bottom:1px solid #f1e4c6;font-family:%s;\
                    font-size:18px;color:#5c0000">%s</td></tr>
                    """
                            .formatted(SANS, esc(row[0]), SERIF, esc(row[1])));
        var footer = new StringBuilder();
        for (var line : contact) footer.append(esc(line)).append("<br>");
        return """
                <!DOCTYPE html>
                <html lang="vi"><head><meta charset="UTF-8">
                <link href="https://fonts.googleapis.com/css2?family=Playfair+Display:ital,wght@0,500;0,700;1,500&display=swap" rel="stylesheet">
                </head>
                <body style="margin:0;padding:0;background:#f7efe3">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" \
                style="background:#f7efe3;padding:32px 12px">
                <tr><td align="center">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" \
                style="max-width:580px;background:#fffdf8;border:1px solid #e8d5a8">
                <tr><td align="center" style="background:#870000;\
                background-image:linear-gradient(180deg,#980000 0%%,#6b0000 100%%);\
                padding:32px 24px 26px;border-bottom:4px solid #c9a24a">
                <img src="%s" alt="%s" width="150" \
                style="display:block;width:150px;max-width:60%%;height:auto;border:0">
                <div style="font-family:%s;font-size:13px;letter-spacing:4px;\
                text-transform:uppercase;color:#e8c872;margin-top:14px">%s</div>
                </td></tr>
                <tr><td align="center" style="padding:36px 40px 8px">
                <div style="font-family:%s;font-size:12px;letter-spacing:3px;\
                text-transform:uppercase;color:#9a7b4f">%s</div>
                <h1 style="margin:10px 0 0;font-family:%s;font-weight:700;font-size:28px;\
                line-height:1.3;color:#870000">%s</h1>
                <div style="width:64px;height:2px;background:#c9a24a;margin:18px auto 0;\
                line-height:2px;font-size:0">&nbsp;</div>
                </td></tr>
                <tr><td style="padding:24px 40px 8px;font-family:%s;font-size:17px;\
                line-height:1.7;color:#3b2a1a">
                <p style="margin:0 0 12px">Kính gửi quý khách <em style="color:#870000">%s</em>,</p>
                <p style="margin:0">%s</p>
                </td></tr>
                <tr><td style="padding:16px 40px 8px">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" \
                style="border-top:1px solid #e8d5a8">
                %s</table>
                </td></tr>
                <!--preorder-->
                <tr><td style="padding:20px 40px 36px;font-family:%s;font-size:13px;\
                line-height:1.6;color:#7a6650">%s</td></tr>
                <tr><td align="center" style="background:#5c0000;padding:22px 24px;\
                border-top:3px solid #c9a24a">
                <div style="font-family:%s;font-size:18px;font-style:italic;color:#e8c872">%s</div>
                <div style="font-family:%s;font-size:12px;line-height:1.7;color:#f3dcc0;\
                margin-top:6px">%s</div>
                </td></tr>
                </table>
                </td></tr></table>
                </body></html>
                """
                .formatted(
                        esc(logo),
                        esc(restaurant),
                        SERIF,
                        esc(restaurant),
                        SANS,
                        esc(kicker),
                        SERIF,
                        esc(title),
                        SERIF,
                        esc(customer),
                        esc(body),
                        details,
                        SANS,
                        esc(note),
                        SERIF,
                        esc(restaurant),
                        SANS,
                        footer);
    }

    private static String esc(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value, "UTF-8");
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
