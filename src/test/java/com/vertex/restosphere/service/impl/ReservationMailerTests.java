package com.vertex.restosphere.service.impl;

import static org.junit.jupiter.api.Assertions.*;

import com.vertex.restosphere.entity.Area;
import com.vertex.restosphere.entity.Reservation;
import com.vertex.restosphere.entity.RestaurantSettings;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ReservationMailerTests {

    @Test
    void rendersDetailsInRestaurantTimezoneAndEscapesHtml() {
        var settings = new RestaurantSettings();
        settings.setName("Sea's Crown");
        settings.setPhone("0901234567");
        var area = new Area();
        area.setName("Khu A");
        var r = new Reservation();
        r.setCode("RS-211126-001");
        r.setCustomerName("<b>An</b>");
        r.setStartsAt(Instant.parse("2026-11-22T05:00:00Z"));
        r.setEndsAt(Instant.parse("2026-11-22T07:00:00Z"));
        r.setGuestCount(4);
        r.setArea(area);

        var mail = ReservationMailer.render(r, settings, "Đã xác nhận", "Hẹn gặp bạn!");

        assertEquals("[Sea's Crown] Đã xác nhận - RS-211126-001", mail.subject());
        assertTrue(mail.html().contains("12:00 – 14:00"), "time shown in Asia/Ho_Chi_Minh");
        assertTrue(mail.html().contains("22/11/2026"));
        assertTrue(mail.html().contains("&lt;b&gt;An&lt;/b&gt;"), "customer name is escaped");
        assertFalse(mail.html().contains("<b>An</b>"));
        assertTrue(mail.text().contains("Mã đặt bàn: RS-211126-001"));
        assertTrue(mail.text().contains("Khu vực: Khu A"));
        assertTrue(mail.text().contains("Điện thoại: 0901234567"));
        assertTrue(mail.html().contains("Hẹn gặp bạn!"), "Vietnamese text is kept as UTF-8");
        assertTrue(mail.html().contains("Khu A"));
        assertFalse(mail.html().contains("Georgia"), "Georgia breaks Vietnamese diacritics");
        assertTrue(mail.inlineLogo(), "falls back to the bundled logo");
        assertTrue(mail.html().contains("cid:restaurant-logo"));
    }

    @Test
    void usesConfiguredLogoUrlInsteadOfInlineLogo() {
        var settings = new RestaurantSettings();
        settings.setName("Sea's Crown");
        settings.setLogoUrl("https://cdn.example.com/logo.png");
        var r = new Reservation();
        r.setCode("RS-211126-002");
        r.setCustomerName("An");
        r.setStartsAt(Instant.parse("2026-11-22T05:00:00Z"));
        r.setGuestCount(2);

        var mail = ReservationMailer.render(r, settings, "Đã nhận", "Cảm ơn");

        assertFalse(mail.inlineLogo());
        assertTrue(mail.html().contains("https://cdn.example.com/logo.png"));
    }
}
