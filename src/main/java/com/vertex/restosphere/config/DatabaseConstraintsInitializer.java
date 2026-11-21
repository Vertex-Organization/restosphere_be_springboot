package com.vertex.restosphere.config;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * JPA không khai báo được index một phần (partial unique index). Idempotent nên chạy được cả trên
 * DB mới lẫn DB cũ mà không cần ai chạy SQL tay. Khi có Flyway thì chuyển các câu này vào
 * migration.
 */
@Component
@RequiredArgsConstructor
class DatabaseConstraintsInitializer implements ApplicationRunner {

    private static final List<String> STATEMENTS =
            List.of(
                    "create unique index if not exists uq_cashier_shifts_open_per_cashier"
                            + " on cashier_shifts (cashier_id) where closed_at is null",
                    "create unique index if not exists uq_table_sessions_open_per_table"
                            + " on table_sessions (table_id) where status <> 'CLOSED'");

    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        STATEMENTS.forEach(jdbc::execute);
    }
}
