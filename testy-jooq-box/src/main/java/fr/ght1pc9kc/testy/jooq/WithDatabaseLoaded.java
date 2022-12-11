package fr.ght1pc9kc.testy.jooq;

import lombok.AllArgsConstructor;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.Location;
import org.flywaydb.core.api.configuration.ClassicConfiguration;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import javax.sql.DataSource;
import java.util.Objects;
import java.util.Optional;

/**
 * <p>This extension depends on a {@link DatasourceExtension} and runs a <a href="https://flywaydb.org/">Flyway</a>
 * migration on the related DB catalog.</p>
 *
 * <p>By default, the SQL scripts have to be located into {@code db.migration.<catalog>} in the classpath, where {@code <catalog>}
 * is the name of DataSource catalog. The names of the SQL files shall match
 * <a href="https://flywaydb.org/documentation/migrations#naming">Flyway naming convention</a>.</p>
 *
 * <p>The SQL scripts are run **before all the test methods**. They are expected to be used to create the database schema.</p>
 *
 * <pre><code>
 * private static final WithInMemoryDatasource wDataSource = WithInMemoryDatasource.builder()
 *         .setCatalog("my_catalog")
 *         .build();
 *
 * // SQL files shall be located in classpath:db.migration.my_catalog
 * private static final WithDatabaseLoaded wDatabaseLoaded = WithDatabaseLoaded.builder()
 *         .setDatasourceExtension(wDataSource)
 *         .build();
 *
 * {@literal @}RegisterExtension
 * static final ChainedExtension chain = ChainedExtension
 *         .outer(wDataSource)
 *         .append(wDatabaseLoaded)
 *         .register();
 * </code></pre>
 */
@AllArgsConstructor
public final class WithDatabaseLoaded implements BeforeAllCallback, BeforeEachCallback {
    private static final String P_LOADED = "dbLoaded_";

    private final DatasourceExtension wDatasource;
    private final @Nullable Location location;

    @Override
    public void beforeAll(ExtensionContext context) {
        String catalog = getContextCatalog(context);
        DataSource dataSource = Objects.requireNonNull(wDatasource.getDataSource(context),
                "DataSource not found in context Store !");

        Location migrationsLocation = Optional.ofNullable(location)
                .orElseGet(() -> new Location("classpath:db/migration/" + catalog));
        Flyway flyway = Flyway.configure()
                .cleanDisabled(false)
                .dataSource(dataSource)
                .schemas(catalog)
                .placeholderReplacement(false)
                .locations(migrationsLocation)
                .load();
        flyway.clean();
        flyway.migrate();

        getStore(context).put(P_LOADED + catalog, true);
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        String catalog = getContextCatalog(context);
        if (getStore(context).get(P_LOADED + catalog) == null) {
            throw new IllegalStateException(getClass().getName() + " must be static and package-protected !");
        }
    }

    private ExtensionContext.Store getStore(ExtensionContext context) {
        return context.getStore(ExtensionContext.Namespace.create(getClass().getName()));
    }

    private String getContextCatalog(ExtensionContext context) {
        return Objects.requireNonNull(wDatasource.getCatalog(context), "Catalog not found in context Store !");
    }

    public static WithDatabaseLoadedBuilder builder() {
        return new WithDatabaseLoadedBuilder();
    }

    /**
     * Builder for {@link WithDatabaseLoaded}
     */
    public static class WithDatabaseLoadedBuilder {
        private DatasourceExtension wDatasource;
        private Location location = null;

        /**
         * <p>Allow to link the {@link DatasourceExtension} with the {@link WithDatabaseLoaded}. The Flyway migrations
         * was applied to the {@link DataSource} created by this extension.</p>
         * <p>This setter is mandatory.</p>
         *
         * @param wDatasource The linked {@link DataSource} extension
         * @return The current builder
         */
        public WithDatabaseLoadedBuilder setDatasourceExtension(DatasourceExtension wDatasource) {
            this.wDatasource = wDatasource;
            return this;
        }

        /**
         * <p>Allow to set the migrations location. To indicate Flyway where to found SQL files.</p>
         *
         * <p>By default, the {@link WithDatabaseLoaded} use {@code db/migration/catalog}, where catalog was the
         * schema given by the {@link DataSource} context</p>
         *
         * <p>This method was not compatible with {@link #useFlywayDefaultLocation()}</p>
         *
         * @param location The migration files location.
         * @return The current builder
         */
        public WithDatabaseLoadedBuilder setMigrationsLocation(String location) {
            this.location = new Location(location);
            return this;
        }

        /**
         * <p>Use the Flyway default migrations location instead a migration with catalog name.</p>
         *
         * <p>By default, the {@link WithDatabaseLoaded} use {@code db/migration/catalog}, where catalog was the
         * schema given by the {@link DataSource} context</p>
         *
         * <p>This method was not compatible with {@link #setMigrationsLocation(String)}</p>
         *
         * @return The current builder
         */
        public WithDatabaseLoadedBuilder useFlywayDefaultLocation() {
            this.location = new ClassicConfiguration().getLocations()[0];
            return this;
        }

        /**
         * Build the {@link WithDatabaseLoaded} extension
         *
         * @return The extension
         */
        public WithDatabaseLoaded build() {
            Objects.requireNonNull(wDatasource, "A DataSource extension was mandatory !");
            return new WithDatabaseLoaded(wDatasource, location);
        }
    }
}
