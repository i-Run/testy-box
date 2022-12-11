package fr.ght1pc9kc.testy.jooq;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WithDatabaseLoadedWithLocationTest {
    @RegisterExtension
    static WithInMemoryDatasource wDs = WithInMemoryDatasource.builder()
            .setCatalog("dummy").build();

    @RegisterExtension
    static WithDatabaseLoaded wDbLoadedLocation = WithDatabaseLoaded.builder()
            .setMigrationsLocation("db/migration/dummy_legacy")
            .setDatasourceExtension(wDs).build();

    @Test
    void should_get_data_form_loaded_db(DataSource ds) throws SQLException {
        List<String> actuals = new ArrayList<>();
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT * FROM GUNGAN");

            while (rs.next()) {
                actuals.add(rs.getString(1) + " " + rs.getString(2));
            }
            rs.close();
        }

        assertThat(actuals).contains("Jar Jar Binks");
    }
}