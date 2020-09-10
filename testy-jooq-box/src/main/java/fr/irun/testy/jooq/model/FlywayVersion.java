package fr.irun.testy.jooq.model;

import lombok.Builder;
import lombok.Value;
import org.flywaydb.core.api.MigrationType;

import java.time.Instant;

/**
 * Model matching a version of Flyway migration.
 */
@Builder
@Value
public class FlywayVersion {

    public final String version;
    public final String description;
    public final MigrationType type;
    public final String script;
    public final Integer checksum;
    public final String installedBy;
    public final Instant installedOn;
    public final Integer executionTime;
    public final Boolean success;
}
