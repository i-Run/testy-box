package fr.irun.test.jooq;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.RenderNameStyle;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.extension.*;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;

import javax.sql.DataSource;

public class WithDslContext implements BeforeEachCallback, ParameterResolver {

    private static final String DSL_CONTEXT = "dslContext";
    private static final String DATASOUCE = "datasource";

    @Override
    public void beforeEach(ExtensionContext context) {
        Settings settings = new Settings();
        settings.setRenderNameStyle(RenderNameStyle.UPPER);
        settings.setRenderSchema(false);

        Store dialectStore = context.getStore(Namespace.create(SQLDialect.class.getPackage().getName()));
        SQLDialect dialect = dialectStore.get(WithMySQLDialect.DSL_DIALECT, SQLDialect.class);

        Store dsStore = context.getStore(Namespace.create(DataSource.class.getPackage().getName(), context.getRequiredTestMethod()));
        DataSource ds = dsStore.get(DATASOUCE, DataSource.class);

        DSLContext dslContext = DSL.using(ds, dialect, settings);
        getStore(context).put(DSL_CONTEXT, dslContext);
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        Class<?> type = parameterContext.getParameter().getType();
        return DSLContext.class.equals(type);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        Class<?> type = parameterContext.getParameter().getType();
        if (DSLContext.class.equals(type)) {
            return getStore(extensionContext).get(DSL_CONTEXT);
        }
        return null;
    }

    private Store getStore(ExtensionContext context) {
        return context.getStore(Namespace.create(getClass(), context.getRequiredTestMethod()));
    }
}
