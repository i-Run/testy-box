package fr.irun.testy.jooq.samples;

import fr.irun.testy.jooq.model.FlywayVersion;
import org.flywaydb.core.api.MigrationType;

import java.time.Instant;

public final class FlywayVersionDataSet {

    public static final FlywayVersion VERSION_1 = FlywayVersion.builder()
            .version("1")
            .installedOn(Instant.EPOCH)
            .checksum(1)
            .description("description-1")
            .executionTime(1)
            .installedBy("user-1")
            .script("script-1")
            .success(true)
            .type(MigrationType.SQL)
            .build();
    public static final FlywayVersion VERSION_2 = FlywayVersion.builder()
            .version("2")
            .installedOn(Instant.EPOCH)
            .checksum(1)
            .description("description-2")
            .executionTime(1)
            .installedBy("user-2")
            .script("script-2")
            .success(true)
            .type(MigrationType.SQL)
            .build();
    public static final FlywayVersion VERSION_3 = FlywayVersion.builder()
            .version("3")
            .installedOn(Instant.EPOCH)
            .checksum(1)
            .description("description-3")
            .executionTime(1)
            .installedBy("user-3")
            .script("script-3")
            .success(true)
            .type(MigrationType.SQL)
            .build();

}
