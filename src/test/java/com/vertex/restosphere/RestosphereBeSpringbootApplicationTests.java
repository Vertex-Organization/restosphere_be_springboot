package com.vertex.restosphere;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

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
class RestosphereBeSpringbootApplicationTests {
    @Autowired private DataSource dataSource;

    @Test
    void contextLoads() {}

    @Test
    void catalogDatasetCanBeAppliedTwice() throws Exception {
        try (var connection = dataSource.getConnection()) {
            var dataset = new ClassPathResource("dataset.sql");
            assertTrue(connection.getMetaData().getURL().contains("jdbc:postgresql:"));
            ScriptUtils.executeSqlScript(connection, dataset);
            var before = snapshot(connection);
            var outsider = UUID.randomUUID();
            try (var statement =
                    connection.prepareStatement(
                            """
                    insert into users (id,email,password_hash,full_name,role,enabled,created_at,updated_at)
                    values (?, 'outside@restosphere.test', 'unchanged', 'Unrelated user', 'WAITER', true, now(), now())
                    """)) {
                statement.setObject(1, outsider);
                statement.executeUpdate();
            }
            try {
                ScriptUtils.executeSqlScript(connection, dataset);
                assertEquals(
                        "unchanged",
                        scalar(
                                connection,
                                "select password_hash from users where email='outside@restosphere.test'"));
            } finally {
                try (var statement = connection.prepareStatement("delete from users where id=?")) {
                    statement.setObject(1, outsider);
                    statement.executeUpdate();
                }
            }
            assertEquals(before, snapshot(connection));
            verifyDemo(connection);
            try (var statement = connection.createStatement();
                    var result =
                            statement.executeQuery(
                                    "select (select count(*) from areas), (select count(*) from"
                                            + " restaurant_tables), (select count(*) from menu_categories),"
                                            + " (select count(*) from menu_items), (select count(*)"
                                            + " from menu_items where image_url like"
                                            + " 'https://res.cloudinary.com/dkm4rdx48/image/upload/%/restosphere/menu/%.jpg')")) {
                result.next();
                assertEquals(2, result.getInt(1));
                assertEquals(24, result.getInt(2));
                assertEquals(10, result.getInt(3));
                assertEquals(23, result.getInt(4));
                assertEquals(23, result.getInt(5));
            }
        }
    }

    private Map<String, String> snapshot(Connection connection) throws Exception {
        var result = new LinkedHashMap<String, String>();
        for (var table :
                new String[] {
                    "users",
                    "areas",
                    "restaurant_tables",
                    "menu_categories",
                    "menu_items",
                    "restaurant_settings",
                    "reservations",
                    "reservation_tables",
                    "reservation_items",
                    "table_sessions",
                    "orders",
                    "order_items",
                    "order_drafts",
                    "bills",
                    "payments",
                    "shifts",
                    "shift_assignments",
                    "cashier_shifts",
                    "approval_requests",
                    "notifications",
                    "notification_reads",
                    "incidents",
                    "audit_logs"
                }) {
            result.put(
                    table,
                    scalar(
                            connection,
                            "select coalesce(jsonb_agg(to_jsonb(t) order by id)::text,'[]') from "
                                    + table
                                    + " t"));
        }
        return result;
    }

    private String scalar(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement();
                var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getString(1);
        }
    }

    private void verifyDemo(Connection connection) throws Exception {
        for (var entry :
                Map.of(
                                "reservations",
                                32,
                                "table_sessions",
                                26,
                                "orders",
                                52,
                                "order_items",
                                104,
                                "bills",
                                26,
                                "payments",
                                24,
                                "shifts",
                                7,
                                "shift_assignments",
                                42,
                                "cashier_shifts",
                                7)
                        .entrySet()) {
            assertEquals(
                    entry.getValue().toString(),
                    scalar(connection, "select count(*) from " + entry.getKey()),
                    entry.getKey());
        }
        assertEquals("7", scalar(connection, "select count(distinct status) from order_items"));
        assertEquals("8", scalar(connection, "select count(distinct status) from reservations"));
        assertEquals(
                "7",
                scalar(
                        connection,
                        "select count(distinct floor(extract(epoch from (a.created_at-s.started_at))/86400)) from table_sessions s cross join audit_logs a where a.id=md5('restosphere:seed:anchor')::uuid"));
        assertEquals("0", scalar(connection, "select count(*) from refresh_tokens"));
        assertEquals(
                "0",
                scalar(
                        connection,
                        """
                select count(*) from bills b where b.subtotal <>
                (select coalesce(sum(round(i.quantity*i.unit_price)),0) from order_items i
                 join orders o on o.id=i.order_id where o.session_id=b.session_id
                 and i.status not in ('CANCELLED','REJECTED','VOIDED'))
                """));
        assertEquals(
                "0",
                scalar(
                        connection,
                        """
                select count(*) from bills b cross join restaurant_settings s where
                b.discount <> case when b.discount_mode='PERCENT' then least(b.subtotal,round(b.subtotal*b.discount_value/100)) else 0 end
                or b.service_charge <> round((b.subtotal-b.discount)*s.service_charge_rate/100)
                or b.tax_amount <> round((b.subtotal-b.discount+b.service_charge)*s.vat_rate/100)
                or b.total <> b.subtotal-b.discount+b.service_charge+b.tax_amount
                """));
        assertEquals(
                "0",
                scalar(
                        connection,
                        """
                select count(*) from bills b join table_sessions s on s.id=b.session_id
                where (b.status='PAID' and (s.status<>'CLOSED' or b.total <>
                    (select coalesce(sum(amount),0) from payments p where p.bill_id=b.id and p.status='COMPLETED')))
                or (b.status='OPEN' and b.total <=
                    (select coalesce(sum(amount),0) from payments p where p.bill_id=b.id and p.status='COMPLETED'))
                or (b.status='VOIDED' and (b.total<>0 or s.status<>'CLOSED'))
                """));
        assertEquals(
                "0",
                scalar(
                        connection,
                        """
                select count(*) from payments p join cashier_shifts cs on cs.id=p.cashier_shift_id
                where p.created_at<cs.opened_at or p.created_at>cs.closed_at
                or (p.status='COMPLETED' and p.paid_at is null)
                or (p.method='CASH' and p.tendered<p.amount)
                """));
        assertEquals(
                "0",
                scalar(
                        connection,
                        """
                select count(*) from cashier_shifts cs where cs.expected_cash <> cs.opening_cash +
                (select coalesce(sum(p.amount),0) from payments p where p.cashier_shift_id=cs.id
                 and p.method='CASH' and p.status='COMPLETED')
                or (cs.closed_at is not null and cs.counted_cash<>cs.expected_cash)
                """));
        assertEquals(
                "0",
                scalar(
                        connection,
                        """
                select count(*) from table_sessions s join reservations r on r.id=s.reservation_id
                join restaurant_tables t on t.id=s.table_id where r.area_id<>t.area_id or s.guest_count>t.capacity
                or not exists (select 1 from reservation_tables rt where rt.reservation_id=r.id and rt.table_id=t.id)
                or (s.status<>'CLOSED' and (t.status<>'OCCUPIED' or r.status<>'SEATED'))
                """));
        assertEquals(
                "0",
                scalar(
                        connection,
                        """
                select count(*) from (select table_id from table_sessions where status<>'CLOSED'
                group by table_id having count(*)>1) duplicates
                """));
        assertEquals(
                "0",
                scalar(
                        connection,
                        """
                select count(*) from order_items i join orders o on o.id=i.order_id
                join table_sessions s on s.id=o.session_id where i.created_at<o.created_at
                or i.started_at<i.created_at or i.ready_at<i.started_at or i.served_at<i.ready_at
                or (s.status='CLOSED' and i.status in ('SUBMITTED','PREPARING','READY'))
                """));
        var encoder = new BCryptPasswordEncoder();
        try (var statement = connection.createStatement();
                var rows =
                        statement.executeQuery(
                                """
                select password_hash from users where id in
                (select md5('restosphere:demo:' || account)::uuid from
                 unnest(array['admin','manager','cashier','kitchen','receptionist','waiter','waiter2']) account)
                """)) {
            int count = 0;
            while (rows.next()) {
                assertTrue(encoder.matches("123456", rows.getString(1)));
                count++;
            }
            assertEquals(7, count);
        }
    }
}
