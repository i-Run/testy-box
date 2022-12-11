package fr.ght1pc9kc.testy.jooq;

import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

class WithDslContextTest {

    @RegisterExtension
    static WithInMemoryDatasource wDs = WithInMemoryDatasource.builder().build();

    @RegisterExtension
    static WithDslContext wDslContext = WithDslContext.builder()
            .setDatasourceExtension(wDs)
            .setDialect(SQLDialect.MYSQL)
            .build();

    @Test
    void should_get_dsl_context(DSLContext tested) {
        assertThat(tested).isNotNull();
        Configuration configuration = tested.configuration();
        assertThat(configuration.dialect()).isEqualTo(SQLDialect.MYSQL);

        int actual = tested.selectOne().execute();
        assertThat(actual).isEqualTo(1);
    }
}