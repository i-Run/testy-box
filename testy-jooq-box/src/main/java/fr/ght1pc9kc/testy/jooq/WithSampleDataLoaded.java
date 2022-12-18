package fr.ght1pc9kc.testy.jooq;

import fr.ght1pc9kc.testy.jooq.model.RelationalDataSet;
import org.jooq.DSLContext;
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
import java.util.concurrent.atomic.AtomicBoolean;

public final class WithSampleDataLoaded implements BeforeAllCallback, BeforeEachCallback, ParameterResolver {
    private static final String P_TRACKER = "sampleTracker_";

    private final WithDslContext wDsl;

    private final List<? extends UpdatableRecord<?>> records;

    private WithSampleDataLoaded(Extension wDsl, List<? extends UpdatableRecord<?>> records) {
        this.wDsl = (WithDslContext) wDsl;
        this.records = List.copyOf(records);
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        final String catalog = getContextCatalog(context);
        getStore(context).put(P_TRACKER + catalog, new Tracker());
        DSLContext dslContext = wDsl.getDslContext(context);
        dslContext.attach(records);
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

        SampleLoaderBuilder(Extension dslExtension) {
            this.dslExtension = dslExtension;
        }

        public <T extends UpdatableRecord<T>> SampleLoaderBuilder addDataset(RelationalDataSet<T> dataset) {
            records.addAll(dataset.records());
            return this;
        }

        public WithSampleDataLoaded build() {
            return new WithSampleDataLoaded(dslExtension, records);
        }
    }

    public static class Tracker {
        private final AtomicBoolean skipNext = new AtomicBoolean(false);

        public void skipNextSampleLoad() {
            skipNext.set(true);
        }
    }
}
