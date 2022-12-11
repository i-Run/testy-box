package fr.ght1pc9kc.testy.jooq;

import fr.ght1pc9kc.testy.jooq.model.FlywayVersion;
import lombok.EqualsAndHashCode;
import org.flywaydb.core.api.configuration.ClassicConfiguration;
import org.jetbrains.annotations.VisibleForTesting;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.InsertValuesStepN;
import org.jooq.Record;
import org.jooq.SQLDialect;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.Internal;
import org.jooq.impl.SQLDataType;
import org.jooq.impl.TableImpl;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Extension used to initialize a Flyway history in a database.
 * A table is created into a schema provided by {@link DatasourceExtension}.
 * History rows can be inserted with model {@link FlywayVersion}.
 * <p>Example of use with default flyway history table</p>
 * <pre><code>
 *     private static final WithInMemoryDatasource wDataSource = WithInMemoryDatasource.builder()
 *             .setCatalog("test_db")
 *             .build();
 *     private static final WithFlywaySchemaHistory wFlywayHistory = WithFlywaySchemaHistory.builder(wDataSource)
 *             .addVersion(FlywayVersion.builder()
 *                              .version("1.0")
 *                              .script("V1_0__my_custom_script.sql")
 *                              .type(MigrationType.SQL)
 *                              .description("my custom script")
 *                              .checksum(1230145397)
 *                              .installedBy("test_db")
 *                              .installedOn(Instant.now())
 *                              .success(true)
 *                              .build)
 *             .build();
 *
 *    {@literal @}RegisterExtension
 *     static final ChainedExtension chain = ChainedExtension.outer(wDataSource)
 *             .append(wFlywayHistory)
 *             .register();
 * </code></pre>
 * <p>A customized history table name can also be defined.</p>
 * <pre><code>
 *     private static final WithInMemoryDatasource wDataSource = WithInMemoryDatasource.builder()
 *             .setCatalog("test_db")
 *             .build();
 *     private static final WithFlywaySchemaHistory wFlywayHistory = WithFlywaySchemaHistory.builder(wDataSource)
 *             .setTableName("my_custom_flyway_history")
 *             .addVersion(FlywayVersion.builder()
 *                              .version("1.0")
 *                              .script("V1_0__my_custom_script.sql")
 *                              .type(MigrationType.SQL)
 *                              .description("my custom script")
 *                              .checksum(1230145397)
 *                              .installedBy("test_db")
 *                              .installedOn(Instant.now())
 *                              .success(true)
 *                              .build)
 *             .build();
 *
 *    {@literal @}RegisterExtension
 *     static final ChainedExtension chain = ChainedExtension.outer(wDataSource)
 *             .append(wFlywayHistory)
 *             .register();
 * </code></pre>
 */
public final class WithFlywaySchemaHistory implements BeforeAllCallback, BeforeEachCallback {

    @VisibleForTesting
    static final String DEFAULT_TABLE_NAME = new ClassicConfiguration().getTable();

    private final DatasourceExtension dataSourceExtension;
    private final List<FlywayVersion> versions;
    private final String tableName;

    private WithFlywaySchemaHistory(DatasourceExtension dataSourceExtension, List<FlywayVersion> versions, String tableName) {
        this.dataSourceExtension = dataSourceExtension;
        this.versions = List.copyOf(versions);
        this.tableName = tableName;
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        final DataSource dataSource = getDataSource(extensionContext);

        final DSLContext dslContext = DSL.using(dataSource, SQLDialect.H2);
        final FlywayTable flywayTable = getFlywayTable(extensionContext);

        dslContext.dropTableIfExists(flywayTable).execute();
        dslContext.createTable(flywayTable).columns(flywayTable.allFields).execute();
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) {
        final DataSource dataSource = getDataSource(extensionContext);
        final FlywayTable flywayTable = getFlywayTable(extensionContext);

        final DSLContext dslContext = DSL.using(dataSource, SQLDialect.H2);

        dslContext.deleteFrom(flywayTable).execute();

        final AtomicInteger installedRank = new AtomicInteger();
        final InsertValuesStepN<Record> query = dslContext.insertInto(flywayTable)
                .columns(flywayTable.allFields);
        versions.stream()
                .map(version -> Arrays.asList(installedRank.addAndGet(1),
                        version.version,
                        version.description,
                        version.type,
                        version.script,
                        version.checksum,
                        version.installedBy,
                        version.installationDate,
                        version.executionTime,
                        version.success
                )).forEach(query::values);
        query.execute();
    }

    private DataSource getDataSource(ExtensionContext context) {
        return Objects.requireNonNull(dataSourceExtension.getDataSource(context), "DataSource not found in Store !");
    }

    private FlywayTable getFlywayTable(ExtensionContext context) {
        final String schema = Objects.requireNonNull(dataSourceExtension.getCatalog(context), "DB schema not found in Store !");
        return new FlywayTable(tableName, schema);
    }

    /**
     * Create a builder for this class.
     *
     * @param datasourceExtension {@link DatasourceExtension} to get the schema where Flyway history table will be created.
     * @return {@link WithFlywaySchemaHistoryBuilder}.
     */
    public static WithFlywaySchemaHistoryBuilder builder(DatasourceExtension datasourceExtension) {
        return new WithFlywaySchemaHistoryBuilder(datasourceExtension);
    }

    /**
     * Builder class for {@link WithFlywaySchemaHistory} extension.
     */
    public static final class WithFlywaySchemaHistoryBuilder {

        private final DatasourceExtension dataSourceExtension;
        private String tableName = DEFAULT_TABLE_NAME;
        private final List<FlywayVersion> allVersions = new ArrayList<>();

        WithFlywaySchemaHistoryBuilder(DatasourceExtension datasourceExtension) {
            this.dataSourceExtension = datasourceExtension;
        }

        /**
         * Define a customized name for the history table.
         * If not set, the name of the table will be given by Flyway basic configuration.
         *
         * @param tableName Name to set for the history table.
         * @return Builder instance.
         */
        public WithFlywaySchemaHistoryBuilder setTableName(String tableName) {
            this.tableName = tableName;
            return this;
        }

        /**
         * Add a version into the history.
         *
         * @param version {@link FlywayVersion} to insert into the history table.
         * @return Builder instance.
         */
        public WithFlywaySchemaHistoryBuilder addVersion(FlywayVersion version) {
            allVersions.add(version);
            return this;
        }

        /**
         * Add many versions into the history.
         *
         * @param versions {@link FlywayVersion} to insert into the history table.
         * @return Builder instance.
         */
        public WithFlywaySchemaHistoryBuilder addVersions(FlywayVersion... versions) {
            allVersions.addAll(Arrays.asList(versions));
            return this;
        }

        /**
         * Build the extension.
         *
         * @return The built {@link WithFlywaySchemaHistory}.
         */
        public WithFlywaySchemaHistory build() {
            return new WithFlywaySchemaHistory(dataSourceExtension, List.copyOf(allVersions), tableName);
        }
    }

    /**
     * Internal Flyway JOOQ table.
     */
    @VisibleForTesting
    @EqualsAndHashCode(callSuper = true)
    static final class FlywayTable extends TableImpl<Record> {

        private static final int VERSION_SIZE = 50;
        private static final int DESCRIPTION_SIZE = 200;
        private static final int TYPE_SIZE = 20;
        private static final int SCRIPT_SIZE = 1000;
        private static final int INSTALLED_BY_SIZE = 100;

        final TableField<Record, Integer> installedRank = createField(DSL.name("installed_rank"), SQLDataType.INTEGER, this, "");
        final TableField<Record, String> version = createField(DSL.name("version"), SQLDataType.VARCHAR(VERSION_SIZE).nullable(false), this, "");
        final TableField<Record, String> description = createField(DSL.name("description"), SQLDataType.VARCHAR(DESCRIPTION_SIZE), this, "");
        final TableField<Record, String> type = createField(DSL.name("type"), SQLDataType.VARCHAR(TYPE_SIZE), this, "");
        final TableField<Record, String> script = createField(DSL.name("script"), SQLDataType.VARCHAR(SCRIPT_SIZE), this, "");
        final TableField<Record, Integer> checksum = createField(DSL.name("checksum"), SQLDataType.INTEGER, this, "");
        final TableField<Record, String> installedBy = createField(DSL.name("installed_by"), SQLDataType.VARCHAR(INSTALLED_BY_SIZE), this, "");
        final TableField<Record, Timestamp> installedOn = createField(DSL.name("installed_on"), SQLDataType.TIMESTAMP.precision(6), this, "");
        final TableField<Record, Integer> executionTime = createField(DSL.name("execution_time"), SQLDataType.INTEGER, this, "");
        final TableField<Record, Boolean> success = createField(DSL.name("success"), SQLDataType.BOOLEAN, this, "");

        private final List<Field<?>> allFields = List.of(
                installedRank,
                version,
                description,
                type,
                script,
                checksum,
                installedBy,
                installedOn,
                executionTime,
                success
        );

        private final AtomicInteger currentRank = new AtomicInteger(0);

        FlywayTable(String tableName, String schema) {
            super(DSL.name(tableName), DSL.schema(DSL.name(schema)));
        }

        @Override
        public UniqueKey<Record> getPrimaryKey() {
            return Internal.createUniqueKey(this, DSL.name("flyway_pk"), installedRank);
        }

    }


}
