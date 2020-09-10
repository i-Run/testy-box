package fr.irun.testy.jooq;

import fr.irun.testy.core.extensions.ChainedExtension;
import fr.irun.testy.jooq.annotations.DbCatalogName;
import fr.irun.testy.jooq.model.FlywayVersion;
import org.flywaydb.core.api.MigrationType;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;
import java.util.stream.Collectors;

import static fr.irun.testy.jooq.samples.FlywayVersionDataSet.VERSION_1;
import static fr.irun.testy.jooq.samples.FlywayVersionDataSet.VERSION_2;
import static fr.irun.testy.jooq.samples.FlywayVersionDataSet.VERSION_3;
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
            .addVersions(VERSION_1, VERSION_2, VERSION_3)
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
                        .installedOn(historyTable.installedOn.get(r).toInstant())
                        .script(historyTable.script.get(r))
                        .success(historyTable.success.get(r))
                        .type(MigrationType.valueOf(historyTable.type.get(r)))
                        .version(historyTable.version.get(r))
                        .build())
                .collect(Collectors.toList());

        assertThat(actual).containsExactly(VERSION_1, VERSION_2, VERSION_3);
    }
}