package fr.irun.test.jooq;

import fr.irun.test.jooq.annotations.DbCatalogName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class WithInMemoryDatasourceTest {

    @Nested
    @ExtendWith(WithInMemoryDatasource.class)
    class WithInMemoryDatasourceSimpleTest {
        @Test
        void should_extend_with_datasource(DataSource tested, @DbCatalogName String catalog) {
            assertThat(catalog).isNotNull();

            try (Connection conn = tested.getConnection();
                 Statement statement = conn.createStatement()) {

                assertThat(conn.getCatalog()).isEqualTo(catalog);
                assertThat(conn.getSchema()).isEqualTo(catalog);

                ResultSet resultSet = statement.executeQuery("SELECT 1");
                assertThat(resultSet).isNotNull();

            } catch (SQLException e) {
                fail(e.getLocalizedMessage(), e);
            }
        }
    }
}