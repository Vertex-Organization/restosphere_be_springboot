package com.vertex.restosphere;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** The dev profile loads the sample data through Flyway, once, after the schema migration. */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("dev")
@SpringBootTest(
        properties = {
            "spring.config.import=",
            "spring.docker.compose.enabled=false",
            "app.mail.enabled=false",
            "app.security.bootstrap-admin.email=",
            "app.security.bootstrap-admin.password="
        })
class DevSeedFlywayTests {
    @Autowired DataSource dataSource;

    @Test
    void seedRunsAfterTheSchemaAndOnlyOnce() {
        var jdbc = new JdbcTemplate(dataSource);
        assertEquals(
                "1,1.1",
                String.join(
                        ",",
                        jdbc.queryForList(
                                "select version from flyway_schema_history where success order by"
                                        + " installed_rank",
                                String.class)));
        assertEquals(
                7,
                jdbc.queryForObject(
                        "select count(*) from employees where email like '%@restosphere.test'",
                        Integer.class));
        assertEquals(26, jdbc.queryForObject("select count(*) from bills", Integer.class));
    }
}
