package fr.ght1pc9kc.testy.jooq;

import fr.ght1pc9kc.testy.core.extensions.ChainedExtension;
import org.assertj.core.api.Assertions;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class WithSampleDataLoadedTest {

    private static final WithInMemoryDatasource wDs = WithInMemoryDatasource.builder().build();
    private static final WithDslContext wDslContext = WithDslContext.builder()
            .setDatasourceExtension(wDs).build();
    private static final WithSampleDataLoaded tested = WithSampleDataLoaded.builder(wDslContext)
            .build();

    @RegisterExtension
    static ChainedExtension ce = ChainedExtension
            .outer(wDs)
            .append(wDslContext)
            .append(tested)
            .register();

    @Test
    void should_get_dsl_context(DSLContext dsl) {
        Assertions.assertThat(dsl).isNotNull();
    }
}