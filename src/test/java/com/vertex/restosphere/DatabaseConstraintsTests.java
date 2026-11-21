package com.vertex.restosphere;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Table;
import jakarta.persistence.metamodel.EntityType;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(
        properties = {
            "spring.config.import=",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "spring.sql.init.mode=never",
            "spring.docker.compose.enabled=false",
            "app.mail.enabled=false",
            "app.security.bootstrap-admin.email=",
            "app.security.bootstrap-admin.password="
        })
@Transactional
class DatabaseConstraintsTests {
    @Autowired private JdbcTemplate jdbc;
    @PersistenceContext private EntityManager em;

    @Test
    void everyDeclaredIndexWasCreated() {
        // Hibernate chỉ cảnh báo khi một câu create index lỗi nên phải đối chiếu với DB.
        Set<String> created =
                new HashSet<>(
                        jdbc.queryForList(
                                "select indexname from pg_indexes where schemaname = current_schema()",
                                String.class));
        Set<String> declared = new HashSet<>();
        for (EntityType<?> type : em.getMetamodel().getEntities()) {
            Table table = type.getJavaType().getAnnotation(Table.class);
            if (table != null) {
                Arrays.stream(table.indexes()).forEach(i -> declared.add(i.name()));
            }
        }
        assertThat(declared).isNotEmpty();
        assertThat(created).containsAll(declared);
    }

    @Test
    void partialUniqueIndexesExist() {
        assertThat(
                        jdbc.queryForList(
                                "select indexname from pg_indexes where indexname in"
                                        + " ('uq_cashier_shifts_open_per_cashier',"
                                        + " 'uq_table_sessions_open_per_table')",
                                String.class))
                .hasSize(2);
    }

    @Test
    void cashierCannotHaveTwoOpenShifts() {
        UUID cashier = UUID.randomUUID();
        jdbc.update(
                "insert into users (id,email,password_hash,full_name,role,enabled,created_at,"
                        + "updated_at) values (?, 'dbc-cashier@restosphere.test', 'x', 'C',"
                        + " 'CASHIER', true, now(), now())",
                cashier);
        openShift(cashier, false);
        openShift(cashier, true);
        assertThatThrownBy(() -> openShift(cashier, false))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void openShift(UUID cashier, boolean closed) {
        jdbc.update(
                "insert into cashier_shifts (id,cashier_id,opened_at,closed_at,opening_cash,"
                        + "created_at,updated_at) values (?, ?, now(), "
                        + (closed ? "now()" : "null")
                        + ", 0, now(), now())",
                UUID.randomUUID(),
                cashier);
    }
}
