package fr.ght1pc9kc.testy.jooq;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class WithDatabaseLoadedWithoutCatalogTest {
    @RegisterExtension
    static WithInMemoryDatasource wDs = WithInMemoryDatasource.builder().build();

    @RegisterExtension
    static WithDatabaseLoaded wDbLoaded = WithDatabaseLoaded.builder()
            .setDatasourceExtension(wDs)
            .useFlywayDefaultLocation()
            .build();

    @Test
    void should_insert_data_to_loaded_db(DataSource ds) throws SQLException {
        int actual;
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {
            actual = stmt.executeUpdate("INSERT INTO JEDI VALUES ( 'LUKE', 'Skywalker', 'LIGHT' )");
        }

        assertThat(actual).isOne();
    }
}