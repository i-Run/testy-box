package fr.irun.test.jooq;

import fr.irun.test.jooq.annotations.DbCatalogName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class WithInMemoryDatasourceRegisterTest {
    private static final String CATALOG_NAME = "comweb_irun";

    @RegisterExtension
    static WithInMemoryDatasource wDs = WithInMemoryDatasource.builder()
            .setCatalog(CATALOG_NAME)
            .wrapTcpServer(true)
            .build();

    @Test
    void should_register_extension_datasource(DataSource tested, @DbCatalogName String catalog) {
        assertThat(catalog).isEqualTo(CATALOG_NAME);

        WithInMemoryDatasourceTest.test_database_aware(tested, CATALOG_NAME);
    }
}
