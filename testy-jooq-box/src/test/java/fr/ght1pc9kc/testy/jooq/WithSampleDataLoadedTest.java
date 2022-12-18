package fr.ght1pc9kc.testy.jooq;

import fr.ght1pc9kc.testy.core.extensions.ChainedExtension;
import fr.ght1pc9kc.testy.dsl.public_.tables.records.JediRecord;
import fr.ght1pc9kc.testy.jooq.samples.JediSampleData;
import fr.ght1pc9kc.testy.jooq.samples.LightSaberSampleData;
import org.assertj.core.api.Assertions;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static fr.ght1pc9kc.testy.dsl.public_.tables.Jedi.JEDI;

class WithSampleDataLoadedTest {

    private static final WithInMemoryDatasource wDs = WithInMemoryDatasource.builder()
            .setCatalog("dummy")
            .build();
    private static final WithDatabaseLoaded wDbLoaded = WithDatabaseLoaded.builder()
            .setDatasourceExtension(wDs)
            .useFlywayDefaultLocation()
            .build();
    private static final WithDslContext wDslContext = WithDslContext.builder()
            .setDatasourceExtension(wDs).build();
    private static final WithSampleDataLoaded tested = WithSampleDataLoaded.builder(wDslContext)
            .addDataset(JediSampleData.DATASET)
            .addDataset(LightSaberSampleData.DATASET)
            .build();

    @RegisterExtension
    static ChainedExtension ce = ChainedExtension
            .outer(wDs)
            .append(wDbLoaded)
            .append(wDslContext)
            .append(tested)
            .register();

    @Test
    @Order(1)
    void should_load_sample_data(DSLContext dsl) {
        Assertions.assertThat(dsl).isNotNull();

        dsl.update(JEDI).set(JediSampleData.OBIWAN.copy().setLastName("Kenoby"))
                .where(JEDI.FIRST_NAME.eq(JediSampleData.OBIWAN.getFirstName())).execute();

        Assertions.assertThat(dsl.selectFrom(JEDI).fetch(JediRecord::getLastName))
                .isNotEmpty().containsExactly("Kenoby", "Master", "Dark");
    }

    @Test
    @Order(2)
    void should_clean_all_records(DSLContext dsl) {
        Assertions.assertThat(dsl).isNotNull();

        Assertions.assertThat(dsl.selectFrom(JEDI).fetch(JediRecord::getLastName))
                .isNotEmpty().containsExactly("Kenobi", "Master", "Dark");
    }
}