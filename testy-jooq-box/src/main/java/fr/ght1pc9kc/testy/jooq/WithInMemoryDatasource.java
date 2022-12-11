package fr.ght1pc9kc.testy.jooq;

import fr.ght1pc9kc.testy.jooq.annotations.DbCatalogName;
import fr.ght1pc9kc.testy.jooq.model.DatabaseTraceLevel;
import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.Server;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.sql.DataSource;
import java.util.Objects;
import java.util.TimeZone;
import java.util.UUID;

/**
 * Allow to create an H2 in-memory database.
 * <p>
 * Usable with {@link org.junit.jupiter.api.extension.ExtendWith} or
 * {@link org.junit.jupiter.api.extension.RegisterExtension}:
 * </p>
 * <pre><code>
 *     {@literal @}RegisterExtension
 *     static WithInMemoryDatasource wDs = WithInMemoryDatasource.builder()
 *             .setCatalog(CATALOG_NAME)
 *             .wrapTcpServer(true)
 *             .build();
 * </code></pre>
 * <p>
 * The default value for the catalog is random UUID. By default the TCP Server was not run.
 * </p>
 * <p>The database parameters :</p>
 * <ul>
 * <li>MODE=MySQL</li>
 * <li>DB_CLOSE_DELAY=-1</li>
 * <li>DATABASE_TO_UPPER=false</li>
 * <li>TIMEZONE=UTC</li>
 * <li>SCHEMA created and set</li>
 * </ul>
 * <p>
 * For inject the auto-generated catalog name use {@link DbCatalogName} annotation
 * </p>
 *
 * @see DbCatalogName
 */
public class WithInMemoryDatasource implements BeforeAllCallback, AfterAllCallback, ParameterResolver, DatasourceExtension {
    private static final Logger LOGGER = LoggerFactory.getLogger(WithInMemoryDatasource.class);
    private static final TimeZone TZ_UTC = TimeZone.getTimeZone("UTC");

    private static final String P_DATASOUCE = "datasource_";
    private static final String P_TCP_SERVER = "tcpServer";
    private static final String P_CATALOG = "catalog_";

    private final String catalog;
    private final boolean withTcpServer;
    private final boolean withReferentialIntegrity;
    private final DatabaseTraceLevel traceLevel;

    public WithInMemoryDatasource() {
        this.catalog = generateRandomCatalogName();
        this.withTcpServer = false;
        this.withReferentialIntegrity = true;
        this.traceLevel = DatabaseTraceLevel.OFF;
    }

    private WithInMemoryDatasource(String catalog, boolean withTcpServer, boolean withReferentialIntegrity, DatabaseTraceLevel traceLevel) {
        this.catalog = Objects.requireNonNull(catalog);
        this.withTcpServer = withTcpServer;
        this.withReferentialIntegrity = withReferentialIntegrity;
        this.traceLevel = traceLevel;
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        TimeZone.setDefault(TZ_UTC);
        Store store = getStore(context);

        JdbcDataSource ds = new JdbcDataSource();
        String databaseUrl = "jdbc:h2:mem:" + catalog + ";"
                + "MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false;"
                + "TRACE_LEVEL_SYSTEM_OUT=" + traceLevel.levelValue + ";"
                + "INIT=CREATE SCHEMA IF NOT EXISTS " + catalog + "\\; "
                + "SET SCHEMA " + catalog + "\\; "
                + "SET REFERENTIAL_INTEGRITY " + Boolean.toString(withReferentialIntegrity).toUpperCase();
        ds.setURL(databaseUrl);
        if (withTcpServer) {
            Server h2TcpServer = Server.createTcpServer("-tcpAllowOthers");
            Server server = h2TcpServer.start();
            LOGGER.info("H2 tcp server started on port: {}", server.getPort());
            store.put(P_TCP_SERVER, h2TcpServer);
        }

        store.put(P_DATASOUCE + catalog, ds);
        store.put(P_CATALOG + catalog, catalog);
    }

    @Override
    public String getCatalog(ExtensionContext context) {
        return getStore(context).get(P_CATALOG + catalog, String.class);
    }

    @Override
    public DataSource getDataSource(ExtensionContext context) {
        return getStore(context).get(P_DATASOUCE + catalog, DataSource.class);
    }

    @Override
    public void afterAll(ExtensionContext context) {
        Store store = getStore(context);
        Server tcpServer = store.get(P_TCP_SERVER, Server.class);
        if (tcpServer != null) {
            tcpServer.stop();
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Class<?> type = parameterContext.getParameter().getType();
        if (DataSource.class.equals(type)) {
            return catalog.equals(getCatalogForParameter(parameterContext));
        } else if (Server.class.equals(type)) {
            return catalog.equals(getCatalogForParameter(parameterContext));
        } else {
            return String.class.equals(type) && parameterContext.isAnnotated(DbCatalogName.class);
        }
    }

    private String getCatalogForParameter(ParameterContext parameterContext) {
        return parameterContext.findAnnotation(Named.class)
                .map(Named::value)
                .orElse(catalog);
    }


    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Class<?> type = parameterContext.getParameter().getType();
        if (DataSource.class.equals(type)) {
            return getStore(extensionContext).get(P_DATASOUCE + getCatalogForParameter(parameterContext), DataSource.class);
        } else if (Server.class.equals(type)) {
            return getStore(extensionContext).get(P_TCP_SERVER, Server.class);
        } else if (String.class.equals(type) && parameterContext.isAnnotated(DbCatalogName.class)) {
            return getStore(extensionContext).get(P_CATALOG + getCatalogForParameter(parameterContext));
        }
        throw new IllegalStateException(getClass().getName() + " must be static and package-protected !");
    }

    private static String generateRandomCatalogName() {
        String uuid = UUID.randomUUID().toString();
        // H2 does not support schema starting with number ...
        return 'd' + uuid.substring(uuid.lastIndexOf('-') + 1);
    }

    private Store getStore(ExtensionContext context) {
        return context.getStore(Namespace.create(getClass().getName()));
    }

    public static WithInMemoryDatasourceBuilder builder() {
        return new WithInMemoryDatasourceBuilder();
    }

    public static class WithInMemoryDatasourceBuilder {
        private String catalog = generateRandomCatalogName();
        private boolean withTcpServer = false;
        private boolean withReferentialIntegrity = true;
        private DatabaseTraceLevel traceLevel = DatabaseTraceLevel.OFF;

        public WithInMemoryDatasourceBuilder setCatalog(String catalog) {
            this.catalog = catalog;
            return this;
        }

        public WithInMemoryDatasourceBuilder wrapTcpServer(boolean withTcpServer) {
            this.withTcpServer = withTcpServer;
            return this;
        }

        public WithInMemoryDatasourceBuilder setReferentialIntegrity(boolean integrity) {
            this.withReferentialIntegrity = integrity;
            return this;
        }

        public WithInMemoryDatasourceBuilder setTraceLevel(DatabaseTraceLevel traceLevel) {
            this.traceLevel = traceLevel;
            return this;
        }

        public WithInMemoryDatasource build() {
            return new WithInMemoryDatasource(this.catalog, this.withTcpServer, this.withReferentialIntegrity, this.traceLevel);
        }
    }
}
