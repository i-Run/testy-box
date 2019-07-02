package fr.irun.testy.jooq;

import fr.irun.testy.core.extensions.ChainedExtension;
import fr.irun.testy.jooq.annotations.DbCatalogName;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.inject.Named;

import static org.assertj.core.api.Assertions.assertThat;

class WithMultipleDataSourcesTest {
    private static final String NORA_CATALOG = "dummy_nora";
    private static final String LEGACY_CATALOG = "dummy_legacy";
    private static final String SQL_SELECT_ALL_JEDIS = "SELECT FIRST_NAME, LAST_NAME FROM JEDI";

    private static WithInMemoryDatasource wLegacyDatasource = WithInMemoryDatasource.builder()
            .setCatalog(LEGACY_CATALOG)
            .setReferentialIntegrity(false)
            .build();
    private static WithDatabaseLoaded wLegacyDatabase = WithDatabaseLoaded.builder()
            .setDatasourceExtension(wLegacyDatasource)
            .build();
    private static WithDslContext wLegacyContext = WithDslContext.builder()
            .setDatasourceExtension(wLegacyDatasource)
            .setDialect(SQLDialect.H2)
            .build();

    private static WithInMemoryDatasource wNoraDatasource = WithInMemoryDatasource.builder()
            .setCatalog(NORA_CATALOG)
            .build();
    private static WithDatabaseLoaded wNoraDatabase = WithDatabaseLoaded.builder()
            .setDatasourceExtension(wNoraDatasource)
            .build();
    private static WithDslContext wNoraContext = WithDslContext.builder()
            .setDatasourceExtension(wNoraDatasource)
            .setDialect(SQLDialect.H2)
            .build();


    @RegisterExtension
    @SuppressWarnings("unused")
    static ChainedExtension dbExtension = ChainedExtension
            .outer(wLegacyDatasource)
            .append(wLegacyDatabase)
            .append(wLegacyContext)
            .append(wNoraDatasource)
            .append(wNoraDatabase)
            .append(wNoraContext)
            .register();

    @Test
    void shouldSetLegacyContext(@Named(LEGACY_CATALOG) DSLContext legacyContext,
                                @Named(LEGACY_CATALOG) @DbCatalogName String legacyCatalog) {

        // Check catalog
        assertThat(legacyCatalog).isEqualTo(LEGACY_CATALOG);

        // Check data
        assertThat(legacyContext).isNotNull();

        final Result<Record> actualLegacyResult = legacyContext.fetch(SQL_SELECT_ALL_JEDIS);
        assertThat(actualLegacyResult).isNotNull();
        assertThat(actualLegacyResult).isNotEmpty();
        assertThat(actualLegacyResult).hasSize(1);

        final Record actualLegacyRecord = actualLegacyResult.get(0);
        assertThat(actualLegacyRecord).isNotNull();
        assertThat(actualLegacyRecord.get(0)).isEqualTo("Dark");
        assertThat(actualLegacyRecord.get(1)).isEqualTo("Vador");
    }

    @Test
    void shouldSetNoraContext(@Named(NORA_CATALOG) DSLContext noraContext,
                              @Named(NORA_CATALOG) @DbCatalogName String noraCatalog) {

        // Check catalog
        assertThat(noraCatalog).isEqualTo(NORA_CATALOG);

        // Check data
        assertThat(noraContext).isNotNull();

        final Result<Record> actualNoraResult = noraContext.fetch(SQL_SELECT_ALL_JEDIS);
        assertThat(actualNoraResult).isNotNull();
        assertThat(actualNoraResult).isNotEmpty();
        assertThat(actualNoraResult).hasSize(1);

        final Record actualNoraRecord = actualNoraResult.get(0);
        assertThat(actualNoraRecord).isNotNull();
        assertThat(actualNoraRecord.get(0)).isEqualTo("Obiwan");
        assertThat(actualNoraRecord.get(1)).isEqualTo("Kenobi");
    }

    @Test
    void shouldSetBothContexts(@Named(LEGACY_CATALOG) DSLContext legacyContext,
                               @Named(LEGACY_CATALOG) @DbCatalogName String legacyCatalog,
                               @Named(NORA_CATALOG) DSLContext noraContext,
                               @Named(NORA_CATALOG) @DbCatalogName String noraCatalog) {

        // Check legacy catalog
        assertThat(legacyCatalog).isEqualTo(LEGACY_CATALOG);

        // Check legacy data
        assertThat(legacyContext).isNotNull();

        final Result<Record> actualLegacyResult = legacyContext.fetch(SQL_SELECT_ALL_JEDIS);
        assertThat(actualLegacyResult).isNotNull();
        assertThat(actualLegacyResult).isNotEmpty();
        assertThat(actualLegacyResult).hasSize(1);

        final Record actualLegacyRecord = actualLegacyResult.get(0);
        assertThat(actualLegacyRecord).isNotNull();
        assertThat(actualLegacyRecord.get(0)).isEqualTo("Dark");
        assertThat(actualLegacyRecord.get(1)).isEqualTo("Vador");

        // Check nora catalog
        assertThat(noraCatalog).isEqualTo(NORA_CATALOG);

        // Check nora data
        assertThat(noraContext).isNotNull();

        final Result<Record> actualNoraResult = noraContext.fetch(SQL_SELECT_ALL_JEDIS);
        assertThat(actualNoraResult).isNotNull();
        assertThat(actualNoraResult).isNotEmpty();
        assertThat(actualNoraResult).hasSize(1);

        final Record actualNoraRecord = actualNoraResult.get(0);
        assertThat(actualNoraRecord).isNotNull();
        assertThat(actualNoraRecord.get(0)).isEqualTo("Obiwan");
        assertThat(actualNoraRecord.get(1)).isEqualTo("Kenobi");
    }


}
