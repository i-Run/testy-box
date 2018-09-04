package fr.irun.test.jooq;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import javax.sql.DataSource;
import java.util.Objects;

public class WithDatabaseLoaded implements BeforeAllCallback, BeforeEachCallback {
    private static final String P_LOADED = "dbLoaded";

    private final DatasourceExtension wDatasource;

    private WithDatabaseLoaded(DatasourceExtension dataSourceProvider) {
        this.wDatasource = dataSourceProvider;
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        String catalog = Objects.requireNonNull(wDatasource.getCatalog(context), "Catalog not found in context Store !");
        DataSource dataSource = Objects.requireNonNull(wDatasource.getDataSource(context), "DataSource not found in context Store !");

        Flyway flyway = new Flyway();
        flyway.setDataSource(dataSource);
        flyway.setSchemas(catalog);
        flyway.setPlaceholderReplacement(false);
        flyway.setLocations("classpath:db.migration." + catalog);
        flyway.migrate();

        getStore(context).put(P_LOADED, true);
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        if (getStore(context).get(P_LOADED) == null) {
            throw new IllegalStateException(getClass().getName() + " must be static and package-protected !");
        }
    }

    private ExtensionContext.Store getStore(ExtensionContext context) {
        return context.getStore(ExtensionContext.Namespace.create(getClass().getName()));
    }

    public static WithDatabaseLoadedBuilder builder() {
        return new WithDatabaseLoadedBuilder();
    }

    public static class WithDatabaseLoadedBuilder {
        private DatasourceExtension wDatasource;

        public WithDatabaseLoadedBuilder setDatasourceExtension(DatasourceExtension wDatasource) {
            this.wDatasource = wDatasource;
            return this;
        }

        public WithDatabaseLoaded build() {
            Objects.requireNonNull(wDatasource, "A DataSource extension was mandatory !");
            return new WithDatabaseLoaded(wDatasource);
        }
    }
}
