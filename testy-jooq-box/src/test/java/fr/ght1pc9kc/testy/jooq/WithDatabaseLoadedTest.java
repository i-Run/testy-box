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

class WithDatabaseLoadedTest {
    @RegisterExtension
    static WithInMemoryDatasource wDs = WithInMemoryDatasource.builder()
            .setCatalog("dummy").build();

    @RegisterExtension
    static WithDatabaseLoaded wDbLoaded = WithDatabaseLoaded.builder()
            .setDatasourceExtension(wDs).build();

    @RegisterExtension
    static WithDatabaseLoaded wDbLoadedLocation = WithDatabaseLoaded.builder()
            .setMigrationsLocation("db/migration/dummy_legacy")
            .setDatasourceExtension(wDs).build();

    @Test
    void should_get_data_form_loaded_db(DataSource ds) throws SQLException {
        int actual = 0;
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {
            actual = stmt.executeUpdate("INSERT INTO JEDI VALUES ( 'LUKE', 'Skywalker', 'LIGHT' )");
        }

        assertThat(actual).isOne();
    }
}