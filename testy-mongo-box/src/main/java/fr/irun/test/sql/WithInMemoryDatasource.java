package fr.irun.test.sql;

import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.Server;
import org.junit.jupiter.api.extension.*;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.sql.DataSource;
import java.util.TimeZone;
import java.util.UUID;

public class WithInMemoryDatasource implements BeforeEachCallback, AfterEachCallback, ParameterResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(WithInMemoryDatasource.class);

    private static final String DATASOUCE = "datasource";
    private static final String TCP_SERVER = "tcpServer";
    public static final String CATALOG = "catalog";

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        String catalog = UUID.randomUUID().toString();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        JdbcDataSource ds = new JdbcDataSource();
//        TRACE_LEVEL_SYSTEM_OUT=3;
        ds.setURL("jdbc:h2:mem:" + catalog + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false;INIT=CREATE SCHEMA IF NOT EXISTS " + catalog + "\\; SET SCHEMA " + catalog);

        Server h2TcpServer = Server.createTcpServer("-tcpAllowOthers");
        LOGGER.info("H2 tcp server started on port: " + h2TcpServer.start().getPort());

        Store store = getStore(context);
        store.put(DATASOUCE, ds);
        store.put(TCP_SERVER, h2TcpServer);
        store.put(CATALOG, catalog);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        getStore(context).get(TCP_SERVER, Server.class).stop();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        Class<?> type = parameterContext.getParameter().getType();
        if (DataSource.class.equals(type))
            return true;
        else if (Server.class.equals(type))
            return true;
        else if (String.class.equals(type)) {
            Named annotation = parameterContext.getParameter().getAnnotation(Named.class);
            return (annotation != null);
        }
        return false;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        Class<?> type = parameterContext.getParameter().getType();
        if (DataSource.class.equals(type))
            return getStore(extensionContext).get(DATASOUCE, DataSource.class);
        else if (Server.class.equals(type))
            return getStore(extensionContext).get(TCP_SERVER, Server.class);
        else if (String.class.equals(type)) {
            Named annotation = parameterContext.getParameter().getAnnotation(Named.class);
            getStore(extensionContext).get(annotation.value());
        }
        return null;
    }

    private Store getStore(ExtensionContext context) {
        return context.getStore(Namespace.create(DataSource.class.getPackage().getName(), context.getRequiredTestMethod()));
    }
}
