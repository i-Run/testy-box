package fr.irun.testy.jooq;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.RenderNameStyle;
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
 *             .setDialect(SQLDialect.MYSQL_5_7)
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
        settings.setRenderNameStyle(RenderNameStyle.UPPER);
        settings.setRenderSchema(false);

        DSLContext dslContext = DSL.using(ds, dialect, settings);

        getStore(context).put(P_DSL_DIALECT, dialect);
        getStore(context).put(P_DSL_CONTEXT, dslContext);
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Class<?> type = parameterContext.getParameter().getType();
        final String catalog = Objects.requireNonNull(wDs.getCatalog(extensionContext), "Catalog not found in context Store !");

        return (DSLContext.class.equals(type) || SQLDialect.class.equals(type)) && catalog.equals(getCatalogForParameter(parameterContext, catalog));
    }

    private String getCatalogForParameter(ParameterContext parameterContext, String catalogDefault) {
        return parameterContext.findAnnotation(Named.class)
                .map(Named::value)
                .orElse(catalogDefault);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Class<?> type = parameterContext.getParameter().getType();
        if (DSLContext.class.equals(type)) {
            return getStore(extensionContext).get(P_DSL_CONTEXT);

        } else if (SQLDialect.class.equals(type)) {
            return getStore(extensionContext).get(P_DSL_DIALECT);
        }

        throw new IllegalStateException(getClass().getName() + " must be static and package-protected !");
    }

    DatasourceExtension getDatasourceExtension() {
        return this.wDs;
    }

    public DSLContext getDslContext(ExtensionContext context) {
        return getStore(context).get(P_DSL_CONTEXT, DSLContext.class);
    }

    private Store getStore(ExtensionContext context) {
        final String catalog = Objects.requireNonNull(wDs.getCatalog(context), "Catalog not found in context Store !");
        return context.getStore(Namespace.create(getClass().getName(), catalog));
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
