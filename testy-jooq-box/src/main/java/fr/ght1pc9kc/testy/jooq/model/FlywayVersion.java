package fr.ght1pc9kc.testy.jooq.model;

import lombok.Builder;
import lombok.Value;
import org.flywaydb.core.extensibility.MigrationType;

import java.time.Instant;

/**
 * Model matching a version of Flyway migration.
 */
@Builder
@Value
public class FlywayVersion {

    /**
     * Version of the executed migration.
     */
    public final String version;

    /**
     * Description of the executed migration.
     */
    public final String description;

    /**
     * Type of the executed migration.
     */
    public final MigrationType type;

    /**
     * Script name for the executed migration.
     */
    public final String script;

    /**
     * Unique checksum of the executed migration.
     */
    public final Integer checksum;

    /**
     * Author of the executed migration.
     */
    public final String installedBy;

    /**
     * Date when the migration has been executed.
     */
    public final Instant installationDate;

    /**
     * Time spent by the migration execution in millis.
     */
    public final Integer executionTime;

    /**
     * Flag indicating if the migration has been successfully executed.
     */
    public final Boolean success;
}
