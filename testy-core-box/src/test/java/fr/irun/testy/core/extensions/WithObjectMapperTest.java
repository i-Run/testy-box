package fr.irun.testy.core.extensions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import fr.irun.testy.core.dummy.Dummy;
import fr.irun.testy.core.dummy.DummyMixin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

class WithObjectMapperTest {

    @Nested
    @ExtendWith(WithObjectMapper.class)
    @DisplayName("Test @ExtendWith(WithObjectMapper.class)")
    class WithObjectMapperTestSimple {
        @Test
        void should_inject_object_mapper(ObjectMapper tested) {
            assertThat(tested.getRegisteredModuleIds()).containsOnly(
                    "com.fasterxml.jackson.datatype.guava.GuavaModule",
                    "com.fasterxml.jackson.datatype.jsr310.JavaTimeModule",
                    "com.fasterxml.jackson.datatype.jdk8.Jdk8Module",
                    "com.fasterxml.jackson.module.paramnames.ParameterNamesModule"
            );

            assertThat(tested.findMixInClassFor(Dummy.class)).isNull();
        }
    }

    @Nested
    @DisplayName("Test @RegisterExtension WithObjectMapper")
    class WithObjectMapperTestComplex {
        @RegisterExtension
        WithObjectMapper wMapper = WithObjectMapper.builder()
                .dontFindAndRegisterModules()
                .addModule(new ParameterNamesModule())
                .addModule(new JavaTimeModule())
                .addMixin(Dummy.class, DummyMixin.class)
                .build();

        @Test
        void should_inject_object_mapper(ObjectMapper tested) {
            assertThat(tested.getRegisteredModuleIds()).containsOnly(
                    "com.fasterxml.jackson.module.paramnames.ParameterNamesModule",
                    "com.fasterxml.jackson.datatype.jsr310.JavaTimeModule"
            );

            assertThat(tested.findMixInClassFor(Dummy.class))
                    .isEqualTo(DummyMixin.class);
        }
    }
}