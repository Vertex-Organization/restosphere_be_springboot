package com.vertex.restosphere.service.impl;

import static org.junit.jupiter.api.Assertions.*;

import com.vertex.restosphere.common.enums.PriceUnit;
import com.vertex.restosphere.entity.Area;
import com.vertex.restosphere.entity.MenuItem;
import com.vertex.restosphere.entity.Reservation;
import com.vertex.restosphere.entity.ReservationItem;
import com.vertex.restosphere.entity.RestaurantSettings;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReservationMailerTests {

    @Test
    void rendersWelcomeEmailForNewMembers() {
        var settings = new RestaurantSettings();
        settings.setName("Sea's Crown");
        var c = new com.vertex.restosphere.entity.Customer();
        c.setFullName("<i>Mai</i>");
        c.setPhone("0901234567");
        c.setCreatedAt(Instant.parse("2026-11-21T18:00:00Z"));

        var mail = ReservationMailer.renderWelcome(c, settings);

        assertEquals("[Sea's Crown] Chào mừng thành viên mới", mail.subject());
        assertTrue(mail.text().contains("Chúc mừng quý khách đã trở thành thành viên"));
        assertTrue(mail.text().contains("Số điện thoại: 0901234567"));
        assertTrue(mail.text().contains("Ngày tham gia: 22/11/2026"), "restaurant timezone");
        assertTrue(mail.html().contains("Thư chào mừng"));
        assertTrue(mail.html().contains("&lt;i&gt;Mai&lt;/i&gt;"), "member name is escaped");
        assertFalse(mail.html().contains("mã đặt bàn"), "no reservation wording");
    }

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

        var mail = ReservationMailer.render(r, settings, "Đã xác nhận", "Hẹn gặp bạn!", List.of());

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

        var mail = ReservationMailer.render(r, settings, "Đã nhận", "Cảm ơn", List.of());

        assertFalse(mail.inlineLogo());
        assertTrue(mail.html().contains("https://cdn.example.com/logo.png"));
    }

    @Test
    void listsPreorderedDishesWithImagesQuantitiesAndSubtotal() {
        var settings = new RestaurantSettings();
        settings.setName("Sea's Crown");
        var r = new Reservation();
        r.setCode("RS-211126-003");
        r.setCustomerName("An");
        r.setStartsAt(Instant.parse("2026-11-22T05:00:00Z"));
        r.setGuestCount(2);
        var crab = new MenuItem();
        crab.setName("Cua hoàng đế");
        crab.setPriceUnit(PriceUnit.KG);
        crab.setImageUrl("https://cdn.example.com/crab.jpg");
        var shrimp = new MenuItem();
        shrimp.setName("Tôm sốt tỏi");
        var a = new ReservationItem();
        a.setMenuItem(crab);
        a.setQuantity(new BigDecimal("1.500"));
        a.setUnitPrice(new BigDecimal("1200000"));
        var b = new ReservationItem();
        b.setMenuItem(shrimp);
        b.setQuantity(new BigDecimal("2"));
        b.setUnitPrice(new BigDecimal("320000"));

        var mail = ReservationMailer.render(r, settings, "Đã nhận", "Cảm ơn", List.of(a, b));

        assertTrue(mail.html().contains("Món đặt trước"));
        assertTrue(mail.html().contains("https://cdn.example.com/crab.jpg"));
        assertTrue(mail.html().contains("1.5 kg"));
        assertTrue(mail.html().contains("× 2"));
        assertTrue(mail.html().contains("2.440.000đ"), "1.5 × 1.200.000 + 2 × 320.000");
        assertTrue(mail.text().contains("- Tôm sốt tỏi × 2: 640.000đ"));
        assertTrue(mail.text().contains("Tạm tính: 2.440.000đ"));
    }

    @Test
    void omitsPreorderSectionWhenNothingWasOrdered() {
        var settings = new RestaurantSettings();
        settings.setName("Sea's Crown");
        var r = new Reservation();
        r.setCode("RS-211126-004");
        r.setCustomerName("An");
        r.setStartsAt(Instant.parse("2026-11-22T05:00:00Z"));
        r.setGuestCount(2);

        var mail = ReservationMailer.render(r, settings, "Đã nhận", "Cảm ơn", List.of());

        assertFalse(mail.html().contains("Món đặt trước"));
        assertFalse(mail.text().contains("Món đặt trước"));
    }

    @Test
    void showsPaidDepositOnlyOnceReceived() {
        var settings = new RestaurantSettings();
        settings.setName("Sea's Crown");
        settings.setBankAccountNumber("0123456789");
        settings.setBankName("VCB");
        var r = new Reservation();
        r.setCode("RS-211126-005");
        r.setCustomerName("An");
        r.setStartsAt(Instant.parse("2026-11-22T05:00:00Z"));
        r.setGuestCount(2);
        r.setDepositAmount(new BigDecimal("300000"));
        r.setDepositDueAt(Instant.parse("2026-11-21T05:15:00Z"));

        var unpaid = ReservationMailer.render(r, settings, "Đã hủy", "Quá hạn", List.of());
        assertFalse(unpaid.text().contains("cọc"), "no transfer instructions in emails");
        assertFalse(unpaid.text().contains("0123456789"));

        r.setDepositPaidAt(Instant.now());
        var paid = ReservationMailer.render(r, settings, "Đã tiếp nhận", "Hẹn gặp", List.of());
        assertTrue(paid.text().contains("Đã cọc: 300.000đ"));
    }
}
