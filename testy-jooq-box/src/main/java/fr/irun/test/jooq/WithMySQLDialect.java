package fr.irun.test.jooq;

import org.jooq.SQLDialect;
import org.junit.jupiter.api.extension.*;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;

public class WithMySQLDialect implements BeforeAllCallback, ParameterResolver {
    private static final Namespace ORG_JOOQ = Namespace.create(SQLDialect.class.getPackage().getName());

    public static final String DSL_DIALECT = "dslDialect";

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        Class<?> type = parameterContext.getParameter().getType();
        return SQLDialect.class.equals(type);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return extensionContext.getStore(ORG_JOOQ).getOrComputeIfAbsent(DSL_DIALECT, (_x) -> SQLDialect.MYSQL_5_7);
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        context.getStore(ORG_JOOQ).put(DSL_DIALECT, SQLDialect.MYSQL_5_7);
    }
}
