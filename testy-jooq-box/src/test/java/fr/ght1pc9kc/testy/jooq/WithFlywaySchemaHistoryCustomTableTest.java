package fr.ght1pc9kc.testy.jooq;

import fr.ght1pc9kc.testy.core.extensions.ChainedExtension;
import fr.ght1pc9kc.testy.jooq.annotations.DbCatalogName;
import fr.ght1pc9kc.testy.jooq.model.FlywayVersion;
import fr.ght1pc9kc.testy.jooq.samples.FlywayVersionDataSet;
import org.flywaydb.core.api.CoreMigrationType;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class WithFlywaySchemaHistoryCustomTableTest {

    private static final String HISTORY_TABLE = "test_history";
    private static final WithInMemoryDatasource wDataSource = WithInMemoryDatasource.builder()
            .setCatalog("test_db")
            .build();
    private static final WithDslContext wDsl = WithDslContext.builder()
            .setDatasourceExtension(wDataSource)
            .build();
    private static final WithFlywaySchemaHistory wFlywayHistory = WithFlywaySchemaHistory.builder(wDataSource)
            .setTableName(HISTORY_TABLE)
            .addVersions(FlywayVersionDataSet.VERSION_1, FlywayVersionDataSet.VERSION_2, FlywayVersionDataSet.VERSION_3)
            .build();

    @RegisterExtension
    static final ChainedExtension chain = ChainedExtension.outer(wDataSource)
            .append(wDsl)
            .append(wFlywayHistory)
            .register();

    private DSLContext dsl;

    @BeforeEach
    void setUp(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Test
    void should_have_inserted_versions_in_custom_table(@DbCatalogName String schema) {
        final WithFlywaySchemaHistory.FlywayTable historyTable = new WithFlywaySchemaHistory.FlywayTable(HISTORY_TABLE, schema);
        final List<FlywayVersion> actual = dsl.selectFrom(HISTORY_TABLE)
                .fetchStream()
                .map(r -> FlywayVersion.builder()
                        .checksum(historyTable.checksum.get(r))
                        .description(historyTable.description.get(r))
                        .executionTime(historyTable.executionTime.get(r))
                        .installedBy(historyTable.installedBy.get(r))
                        .installationDate(historyTable.installedOn.get(r).toInstant())
                        .script(historyTable.script.get(r))
                        .success(historyTable.success.get(r))
                        .type(CoreMigrationType.fromString(historyTable.type.get(r)))
                        .version(historyTable.version.get(r))
                        .build())
                .collect(Collectors.toList());

        assertThat(actual).containsExactly(FlywayVersionDataSet.VERSION_1, FlywayVersionDataSet.VERSION_2, FlywayVersionDataSet.VERSION_3);
    }
}