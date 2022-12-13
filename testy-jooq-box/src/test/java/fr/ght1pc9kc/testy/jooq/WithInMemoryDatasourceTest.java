package fr.ght1pc9kc.testy.jooq;

import fr.ght1pc9kc.testy.jooq.annotations.DbCatalogName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@ExtendWith(WithInMemoryDatasource.class)
class WithInMemoryDatasourceTest {

    @Test
    void should_extend_with_datasource(DataSource tested, @DbCatalogName String catalog) {
        assertThat(catalog).isNotNull();

        test_database_aware(tested, catalog);
    }

    static class WithInMemoryDatasourceRegisterTest {
        static final String CATALOG_NAME = "jedidb";

        @RegisterExtension
        static WithInMemoryDatasource wDs = WithInMemoryDatasource.builder()
                .setCatalog(CATALOG_NAME)
                .wrapTcpServer(true)
                .build();

        @Test
        void should_register_extension_datasource(DataSource tested, @DbCatalogName String catalog) {
            assertThat(catalog).isEqualTo(CATALOG_NAME);

            test_database_aware(tested, CATALOG_NAME);
        }

    }

    public static void test_database_aware(DataSource tested, String expectedCatalog) {
        try (Connection conn = tested.getConnection();
             Statement statement = conn.createStatement()) {

            assertThat(conn.getCatalog()).isEqualTo(expectedCatalog);
            assertThat(conn.getSchema()).isEqualTo(expectedCatalog);

            ResultSet resultSet = statement.executeQuery("SELECT 1");
            assertThat(resultSet).isNotNull();

        } catch (SQLException e) {
            fail(e.getLocalizedMessage(), e);
        }
    }
}