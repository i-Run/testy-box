package fr.irun.test.sql;

import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith({WithInMemoryDatasource.class, WithMySQLDialect.class, WithDslContext.class})
class WithDslContextTest {

    @Test
    void should_use_nested_extensions(DSLContext dsl) {
        assertNotNull(dsl);
    }
}