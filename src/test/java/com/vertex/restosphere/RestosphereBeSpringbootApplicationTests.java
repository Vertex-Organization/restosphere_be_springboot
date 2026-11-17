package com.vertex.restosphere;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import javax.sql.DataSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class RestosphereBeSpringbootApplicationTests {
    @Autowired
    private DataSource dataSource;

    @Test
    void contextLoads() {
    }

    @Test
    void catalogDatasetCanBeAppliedTwice() throws Exception {
        try (var connection = dataSource.getConnection()) {
            var dataset = new ClassPathResource("dataset.sql");
            ScriptUtils.executeSqlScript(connection, dataset);
            ScriptUtils.executeSqlScript(connection, dataset);
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("select (select count(*) from areas), "
                         + "(select count(*) from tables), (select count(*) from menu_categories), "
                         + "(select count(*) from menu_items), "
                         + "(select count(*) from menu_items where image_url like 'https://res.cloudinary.com/dkm4rdx48/image/upload/%/restosphere/menu/%.jpg')")) {
                result.next();
                assertEquals(2, result.getInt(1));
                assertEquals(24, result.getInt(2));
                assertEquals(10, result.getInt(3));
                assertEquals(23, result.getInt(4));
                assertEquals(23, result.getInt(5));
            }
        }
    }

}
