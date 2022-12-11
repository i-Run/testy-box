package fr.ght1pc9kc.testy.jooq;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.RenderNameCase;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

import javax.inject.Named;
import javax.sql.DataSource;
import java.util.Objects;


/**
 * Provide a {@link DSLContext} for the Unit Test
 * <p>Usage:</p>
 * <pre><code>
 *     {@literal @}RegisterExtension
 *     static WithInMemoryDatasource wDs = WithInMemoryDatasource.builder().build();
 *
 *     {@literal @}RegisterExtension
 *     static WithDslContext wDslContext = WithDslContext.builder()
 *             .setDatasourceExtension(wDs)
 *             .setDialect(SQLDialect.MYSQL)
 *             .build();
 * </code></pre>
 * <p>
 * The default value for {@link SQLDialect} is {@link SQLDialect#H2}
 * </p>
 */
public final class WithDslContext implements BeforeAllCallback, ParameterResolver {

    private static final String P_DSL_CONTEXT = "dslContext";
    private static final String P_DSL_DIALECT = "dslDialect";

    private final DatasourceExtension wDs;
    private final SQLDialect dialect;

    private WithDslContext(DatasourceExtension wDs, SQLDialect dialect) {
        this.wDs = wDs;
        this.dialect = dialect;
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        DataSource ds = Objects.requireNonNull(wDs.getDataSource(context), "Datasource not found in Store !");

        Settings settings = new Settings();
        settings.setRenderNameCase(RenderNameCase.UPPER);
        settings.setRenderSchema(false);

        DSLContext dslContext = DSL.using(ds, dialect, settings);

        final String catalog = getContextCatalog(context);
        getStore(context).put(P_DSL_DIALECT + catalog, dialect);
        getStore(context).put(P_DSL_CONTEXT + catalog, dslContext);
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Class<?> type = parameterContext.getParameter().getType();
        final String catalog = getContextCatalog(extensionContext);

        return (DSLContext.class.equals(type) || SQLDialect.class.equals(type))
                && catalog.equals(getCatalogForParameter(parameterContext, extensionContext));
    }

    private String getCatalogForParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return parameterContext.findAnnotation(Named.class)
                .map(Named::value)
                .orElseGet(() -> getContextCatalog(extensionContext));
    }

    private String getContextCatalog(ExtensionContext context) {
        return Objects.requireNonNull(wDs.getCatalog(context), "Catalog not found in context Store !");
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Class<?> type = parameterContext.getParameter().getType();
        final String catalog = getCatalogForParameter(parameterContext, extensionContext);
        if (DSLContext.class.equals(type)) {
            return getStore(extensionContext).get(P_DSL_CONTEXT + catalog);

        } else if (SQLDialect.class.equals(type)) {
            return getStore(extensionContext).get(P_DSL_DIALECT + catalog);
        }

        throw new IllegalStateException(getClass().getName() + " must be static and package-protected !");
    }

    DatasourceExtension getDatasourceExtension() {
        return this.wDs;
    }

    public DSLContext getDslContext(ExtensionContext context) {
        final String catalog = getContextCatalog(context);
        return getStore(context).get(P_DSL_CONTEXT + catalog, DSLContext.class);
    }

    private Store getStore(ExtensionContext context) {
        return context.getStore(Namespace.create(getClass().getName()));
    }

    public static WithDslContextBuilder builder() {
        return new WithDslContextBuilder();
    }

    public static class WithDslContextBuilder {
        private DatasourceExtension wDs;
        private SQLDialect dialect = SQLDialect.H2;

        public WithDslContextBuilder setDatasourceExtension(DatasourceExtension wDs) {
            this.wDs = wDs;
            return this;
        }

        public WithDslContextBuilder setDialect(SQLDialect dialect) {
            this.dialect = dialect;
            return this;
        }

        public WithDslContext build() {
            Objects.requireNonNull(wDs, "DataSource is mandatory for building DSLContext !");
            return new WithDslContext(wDs, dialect);
        }
    }
}
