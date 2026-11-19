package com.vertex.restosphere.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Bao cao doanh thu trang Manager; tien/so luong BigDecimal (mon theo kg co so le), so dem long. */
public record ManagerReportResponse(LocalDate from, LocalDate to, Summary summary, List<Daily> daily,
        List<Dish> dishes, Operations operations) {

    public record Summary(BigDecimal revenue, long payments, long bills, BigDecimal averageBill, long guests) {
    }

    public record Daily(LocalDate day, long payments, BigDecimal revenue) {
    }

    public record Dish(String name, BigDecimal quantity, BigDecimal revenue) {
    }

    public record Operations(long paidGuests, long reservations, long arrived, long cancelledOrNoShow) {
    }
}
