package com.vertex.restosphere.service.impl;

import com.vertex.restosphere.entity.Reservation;
import com.vertex.restosphere.entity.RestaurantSettings;
import com.vertex.restosphere.service.WorkspaceStore;
import jakarta.mail.MessagingException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
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
        var content = render(r, s, subject, body);
        String to = r.getEmail();
        String code = r.getCode();
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
                        log.warn("Reservation email to {} failed: {}", code, e.getMessage());
                    }
                };
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            task.run();
                        }
                    });
        else task.run();
    }

    static Content render(Reservation r, RestaurantSettings s, String subject, String body) {
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
        var contact = new ArrayList<String>();
        if (notBlank(s.getAddress())) contact.add(s.getAddress());
        if (notBlank(s.getPhone())) contact.add("Điện thoại: " + s.getPhone());
        boolean customLogo = notBlank(s.getLogoUrl());
        String logo = customLogo ? s.getLogoUrl() : "cid:" + LOGO_CID;
        String fullSubject = "[" + s.getName() + "] " + subject + " - " + r.getCode();
        return new Content(
                fullSubject,
                text(s.getName(), r.getCustomerName(), body, rows, contact),
                html(logo, s.getName(), subject, r.getCustomerName(), body, rows, contact),
                !customLogo && DEFAULT_LOGO.exists());
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
            String title,
            String customer,
            String body,
            List<String[]> rows,
            List<String> contact) {
        String code = rows.getFirst()[1];
        var grid = new StringBuilder();
        var cells = rows.subList(1, rows.size());
        for (int i = 0; i < cells.size(); i += 2) {
            grid.append("<tr>").append(cell(cells.get(i)));
            grid.append(i + 1 < cells.size() ? cell(cells.get(i + 1)) : "<td width=\"50%\"></td>");
            grid.append("</tr>");
        }
        var footer = new StringBuilder();
        for (var line : contact) footer.append(esc(line)).append("<br>");
        return """
                <!DOCTYPE html>
                <html lang="vi"><head><meta charset="UTF-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <link href="https://fonts.googleapis.com/css2?family=Playfair+Display:ital,wght@0,500;0,700;1,500&display=swap" rel="stylesheet">
                </head>
                <body style="margin:0;padding:0;background:#efe4d2">
                <div style="display:none;max-height:0;overflow:hidden;opacity:0">%s · %s</div>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" \
                style="background:#efe4d2;padding:36px 12px">
                <tr><td align="center">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" \
                style="max-width:600px;background:#fffdf8;border:1px solid #d9bf86;\
                box-shadow:0 12px 32px rgba(92,0,0,.18)">
                <tr><td style="height:6px;background:#c9a24a;font-size:0;line-height:0">&nbsp;</td></tr>
                <tr><td align="center" style="background:#870000;\
                background-image:radial-gradient(circle at 50%% 0%%,#b40000 0%%,#870000 55%%,\
                #5c0000 100%%);padding:40px 24px 34px">
                <img src="%s" alt="%s" width="170" \
                style="display:block;width:170px;max-width:60%%;height:auto;border:0">
                <div style="font-family:%s;font-size:12px;letter-spacing:6px;\
                text-transform:uppercase;color:#e8c872;margin-top:18px">%s</div>
                <div style="font-family:%s;font-size:14px;color:#c9a24a;letter-spacing:8px;\
                margin-top:10px">&#9670; &#9670; &#9670;</div>
                </td></tr>
                <tr><td style="height:2px;background:#c9a24a;font-size:0;line-height:0">&nbsp;</td></tr>
                <tr><td align="center" style="padding:44px 44px 6px">
                <div style="font-family:%s;font-size:11px;letter-spacing:4px;\
                text-transform:uppercase;color:#a8854f">Thông báo đặt bàn</div>
                <h1 style="margin:12px 0 0;font-family:%s;font-weight:700;font-size:32px;\
                line-height:1.25;color:#870000">%s</h1>
                <div style="font-family:%s;font-size:14px;color:#c9a24a;margin-top:14px;\
                letter-spacing:6px">&#8212;&#8212; &#9670; &#8212;&#8212;</div>
                </td></tr>
                <tr><td style="padding:22px 52px 8px;font-family:%s;font-size:17px;\
                line-height:1.75;color:#3b2a1a;text-align:center">
                <p style="margin:0 0 10px">Kính gửi quý khách \
                <span style="font-style:italic;color:#870000;font-weight:700">%s</span>,</p>
                <p style="margin:0">%s</p>
                </td></tr>
                <tr><td align="center" style="padding:28px 44px 8px">
                <table role="presentation" cellpadding="0" cellspacing="0" \
                style="border:2px dashed #c9a24a;background:#fff6e6">
                <tr><td align="center" style="padding:16px 36px">
                <div style="font-family:%s;font-size:11px;letter-spacing:4px;\
                text-transform:uppercase;color:#a8854f">Mã đặt bàn</div>
                <div style="font-family:%s;font-size:30px;font-weight:700;letter-spacing:3px;\
                color:#870000;margin-top:6px">%s</div>
                </td></tr></table>
                </td></tr>
                <tr><td style="padding:24px 44px 8px">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" \
                style="border-collapse:separate;border-spacing:8px">%s</table>
                </td></tr>
                <tr><td align="center" style="padding:18px 52px 40px;font-family:%s;font-size:13px;\
                line-height:1.7;color:#7a6650">Vui lòng giữ mã đặt bàn để tra cứu hoặc hủy lịch \
                trên trang web của nhà hàng.<br>\
                <span style="font-family:%s;font-style:italic;font-size:16px;color:#870000">\
                Rất hân hạnh được đón tiếp quý khách.</span></td></tr>
                <tr><td style="height:2px;background:#c9a24a;font-size:0;line-height:0">&nbsp;</td></tr>
                <tr><td align="center" style="background:#5c0000;padding:28px 24px">
                <div style="font-family:%s;font-size:20px;font-style:italic;color:#e8c872">%s</div>
                <div style="font-family:%s;font-size:12px;line-height:1.8;color:#f3dcc0;\
                margin-top:8px">%s</div>
                <div style="font-family:%s;font-size:11px;color:#b98f8f;margin-top:14px">\
                Email được gửi tự động, vui lòng không trả lời thư này.</div>
                </td></tr>
                </table>
                </td></tr></table>
                </body></html>
                """
                .formatted(
                        esc(title),
                        esc(code),
                        esc(logo),
                        esc(restaurant),
                        SANS,
                        esc(restaurant),
                        SANS,
                        SANS,
                        SERIF,
                        esc(title),
                        SANS,
                        SERIF,
                        esc(customer),
                        esc(body),
                        SANS,
                        SERIF,
                        esc(code),
                        grid,
                        SANS,
                        SERIF,
                        SERIF,
                        esc(restaurant),
                        SANS,
                        footer,
                        SANS);
    }

    private static String cell(String[] row) {
        return """
                <td width="50%%" valign="top" style="background:#fbf3e4;border-left:3px solid #c9a24a;\
                padding:14px 16px"><div style="font-family:%s;font-size:11px;letter-spacing:2px;\
                text-transform:uppercase;color:#a8854f">%s</div><div style="font-family:%s;\
                font-size:19px;color:#5c0000;margin-top:4px">%s</div></td>"""
                .formatted(SANS, esc(row[0]), SERIF, esc(row[1]));
    }

    private static String esc(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value, "UTF-8");
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
