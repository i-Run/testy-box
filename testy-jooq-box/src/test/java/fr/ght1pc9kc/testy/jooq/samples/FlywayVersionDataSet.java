package fr.ght1pc9kc.testy.jooq.samples;

import fr.ght1pc9kc.testy.jooq.model.FlywayVersion;
import org.flywaydb.core.api.CoreMigrationType;

import java.time.Instant;

public final class FlywayVersionDataSet {

    public static final FlywayVersion VERSION_1 = FlywayVersion.builder()
            .version("1")
            .installationDate(Instant.EPOCH)
            .checksum(1)
            .description("description-1")
            .executionTime(1)
            .installedBy("user-1")
            .script("script-1")
            .success(true)
            .type(CoreMigrationType.SQL)
            .build();
    public static final FlywayVersion VERSION_2 = FlywayVersion.builder()
            .version("2")
            .installationDate(Instant.EPOCH)
            .checksum(1)
            .description("description-2")
            .executionTime(2)
            .installedBy("user-2")
            .script("script-2")
            .success(true)
            .type(CoreMigrationType.SQL)
            .build();
    public static final FlywayVersion VERSION_3 = FlywayVersion.builder()
            .version("3")
            .installationDate(Instant.EPOCH)
            .checksum(1)
            .description("description-3")
            .executionTime(3)
            .script("script-3")
            .success(true)
            .type(CoreMigrationType.SQL)
            .build();

}
