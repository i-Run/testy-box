package fr.ght1pc9kc.testy.jooq;

import org.junit.jupiter.api.extension.ExtensionContext;

import javax.sql.DataSource;

/**
 * Provide a {@link DataSource} as an JUnit 5 extension
 */
public interface DatasourceExtension {
    /**
     * Retrieve the {@link DataSource} from the contextual extension Store
     *
     * @param context The extension context
     * @return A usable DataSource
     */
    DataSource getDataSource(ExtensionContext context);

    /**
     * Retrieve the name of the DataSource catalog
     *
     * @param context The extension context
     * @return The catalog name
     */
    String getCatalog(ExtensionContext context);
}
