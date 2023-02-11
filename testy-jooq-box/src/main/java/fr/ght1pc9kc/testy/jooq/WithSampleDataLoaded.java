package fr.ght1pc9kc.testy.jooq;

import fr.ght1pc9kc.testy.jooq.model.RelationalDataSet;
import org.jooq.DSLContext;
import org.jooq.Key;
import org.jooq.Query;
import org.jooq.TableRecord;
import org.jooq.UpdatableRecord;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

import javax.inject.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This extension allows you to load test data into a previously created database.
 * <p>
 * These data are imported as a list of jOOQ records. The records are inserted in the same order they have been added
 * to the extension in order to consider the external key constraints.
 * <p>
 * It is possible to insert data in an empty database thanks to the createTablesIfNotExists option.
 * <p>
 * The concerned tables are emptied and reloaded for each test to keep the consistency, however, for performance
 * reasons, if the data has not been modified during a test, a tracker allows to signal to the extension that it is
 * not necessary to refresh the data. This can improve test performance significantly.
 *
 * <pre><code>
 * // Declare InMemory database
 * private static final WithInMemoryDatasource wDs = WithInMemoryDatasource.builder().build();
 *
 * // Declare jOOQ DslContext
 * private static final WithDslContext wDslContext = WithDslContext.builder()
 *         .setDatasourceExtension(wDs).build();
 *
 * // Declare records sample for each used tables
 * private static final WithSampleDataLoaded wSamples = WithSampleDataLoaded.builder(wDslContext)
 *         .createTablesIfNotExists()               // Create table if not exist in database
 *         .addDataset(UsersRecordSamples.SAMPLE)
 *         .addDataset(UsersRolesSamples.SAMPLE)
 *         .build();
 *
 * // Use ChainedExtension to chain each declared extensions
 *{@literal @}RegisterExtension
 * static ChainedExtension chain = ChainedExtension.outer(wDs)
 *         .append(wDslContext)
 *         .append(wSamples)
 *         .register();
 * </code></pre>
 *
 * You can now write your test
 * <pre><code>
 *{@literal @}Test
 * void should_get_raw_news(WithSampleDataLoaded.Tracker tracker) { // Inject the modification tracker
 *     tracker.skipNextSampleLoad();    // ask for skipping the next sample reload
 *
 *     // ...
 * }
 * </code></pre>
 */
public final class WithSampleDataLoaded implements BeforeAllCallback, BeforeEachCallback, ParameterResolver {
    private static final String P_TRACKER = "sampleTracker_";

    private final WithDslContext wDsl;

    private final List<? extends UpdatableRecord<?>> records;
    private final boolean createTables;

    private WithSampleDataLoaded(Extension wDsl, List<? extends UpdatableRecord<?>> records, boolean createTables) {
        this.wDsl = (WithDslContext) wDsl;
        this.records = List.copyOf(records);
        this.createTables = createTables;
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        final String catalog = getContextCatalog(context);
        getStore(context).put(P_TRACKER + catalog, new Tracker());
        DSLContext dslContext = wDsl.getDslContext(context);
        dslContext.attach(records);

        if (createTables) {
            records.stream()
                    .map(TableRecord::getTable)
                    .distinct()
                    .map(t -> dslContext.createTableIfNotExists(t)
                            .columns(t.fields())
                            .primaryKey(Optional.ofNullable(t.getPrimaryKey()).map(Key::getFields).orElse(null))
                            .constraints(t.getKeys().stream().map(Key::constraint).toList())
                    )
                    .forEach(Query::execute);
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        final String catalog = getContextCatalog(context);
        Tracker tracker = getStore(context).get(P_TRACKER + catalog, Tracker.class);
        if (tracker == null) {
            throw new IllegalStateException(getClass().getName() + " must be static and package-protected !");
        }

        if (tracker.skipNext.getAndSet(false)) {
            return;
        }

        DSLContext dslContext = wDsl.getDslContext(context);
        dslContext.transaction(tx -> {
            DSLContext txDsl = DSL.using(tx);

            var it = records.listIterator(records.size());
            while (it.hasPrevious()) {
                txDsl.delete(it.previous().getTable()).execute();
            }
            records.forEach(r -> r.changed(true));
            txDsl.batchInsert(records).execute();
        });
    }

    private ExtensionContext.Store getStore(ExtensionContext context) {
        return context.getStore(ExtensionContext.Namespace.create(getClass().getName(), getContextCatalog(context)));
    }

    private String getContextCatalog(ExtensionContext context) {
        return Objects.requireNonNull(wDsl.getDatasourceExtension().getCatalog(context), "Catalog not found in context Store !");
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Class<?> type = parameterContext.getParameter().getType();
        final String catalog = getContextCatalog(extensionContext);
        return Tracker.class.equals(type) && catalog.equals(getCatalogForParameter(parameterContext, catalog));
    }

    private String getCatalogForParameter(ParameterContext parameterContext, String catalogDefault) {
        return parameterContext.findAnnotation(Named.class)
                .map(Named::value)
                .orElse(catalogDefault);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Class<?> type = parameterContext.getParameter().getType();
        final String catalog = getContextCatalog(extensionContext);
        if (Tracker.class.equals(type)) {
            return getStore(extensionContext).get(P_TRACKER + catalog);
        }

        throw new NoSuchElementException(P_TRACKER + catalog);
    }

    public static SampleLoaderBuilder builder(Extension ex) {
        return new SampleLoaderBuilder(ex);
    }

    public static class SampleLoaderBuilder {
        private final Extension dslExtension;
        private final List<UpdatableRecord<?>> records = new ArrayList<>();
        private boolean createTables = false;

        SampleLoaderBuilder(Extension dslExtension) {
            this.dslExtension = dslExtension;
        }

        public <T extends UpdatableRecord<T>> SampleLoaderBuilder addDataset(RelationalDataSet<T> dataset) {
            records.addAll(dataset.records());
            return this;
        }

        /**
         * With this option, the tables corresponding to the sample records will be created if they do not exist.
         * <p>
         * This avoids having to load the complete database schema and can speed up the tests significantly.
         * <p>
         * However, since the schema is not loaded, not all the data it contains is loaded either,
         * which can cause unexpected behavior.
         * <p>
         * By default, the tables was never created.
         *
         * @return the builder instance
         */
        public SampleLoaderBuilder createTablesIfNotExists() {
            this.createTables = true;
            return this;
        }

        public WithSampleDataLoaded build() {
            return new WithSampleDataLoaded(dslExtension, records, createTables);
        }
    }

    public static class Tracker {
        private final AtomicBoolean skipNext = new AtomicBoolean(false);

        public void skipNextSampleLoad() {
            skipNext.set(true);
        }
    }
}
