package fr.irun.testy.jooq;

import fr.irun.testy.jooq.model.RelationalDataSet;
import org.jooq.DSLContext;
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

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WithSampleDataLoaded implements BeforeAllCallback, BeforeEachCallback, ParameterResolver {
    private static final String P_TRACKER = "sampleTracker";

    private final WithDslContext wDsl;

    private final List<? extends UpdatableRecord<?>> records;

    private WithSampleDataLoaded(Extension wDsl, List<? extends UpdatableRecord<?>> records) {
        this.wDsl = (WithDslContext) wDsl;
        this.records = records;
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        getStore(context).put(P_TRACKER, new Tracker());
        DSLContext dslContext = wDsl.getDslContext(context);
        dslContext.attach(records);
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        Tracker tracker = getStore(context).get(P_TRACKER, Tracker.class);
        if (tracker == null) {
            throw new IllegalStateException(getClass().getName() + " must be static and package-protected !");
        }

        if (tracker.skipNext.getAndSet(false)) {
            return;
        }

        DSLContext dslContext = wDsl.getDslContext(context);
        dslContext.transaction(tx -> {
            DSLContext txDsl = DSL.using(tx);
            records.stream().map(TableRecord::getTable).distinct()
                    .map(txDsl::truncate)
                    .forEach(Query::execute);
            records.forEach(r -> r.changed(true));
            txDsl.batchInsert(records).execute();
        });
    }

    private ExtensionContext.Store getStore(ExtensionContext context) {
        return context.getStore(ExtensionContext.Namespace.create(getClass().getName()));
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Class<?> type = parameterContext.getParameter().getType();
        return Tracker.class.equals(type);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Class<?> type = parameterContext.getParameter().getType();
        if (Tracker.class.equals(type)) {
            return getStore(extensionContext).get(P_TRACKER);
        }

        throw new NoSuchElementException(P_TRACKER);
    }

    public static SampleLoaderBuilder builder(Extension ex) {
        return new SampleLoaderBuilder(ex);
    }

    public static class SampleLoaderBuilder {
        private final Extension dslExtension;
        private final List<? extends UpdatableRecord<?>> records = new ArrayList<>();

        SampleLoaderBuilder(Extension dslExtension) {
            this.dslExtension = dslExtension;
        }

        @SuppressWarnings("unchecked")
        public SampleLoaderBuilder addDataset(RelationalDataSet dataset) {
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
