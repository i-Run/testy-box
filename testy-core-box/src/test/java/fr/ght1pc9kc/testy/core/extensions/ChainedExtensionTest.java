package fr.ght1pc9kc.testy.core.extensions;

import fr.ght1pc9kc.testy.core.dummy.Dummy;
import fr.ght1pc9kc.testy.core.dummy.DummyExtension;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class ChainedExtensionTest {

    private static final List<String> calls = new ArrayList<>();

    private static DummyExtension wDummyOuter = new DummyExtension(calls, "outer", new Dummy("off", "rab"));

    private static DummyExtension wDummyInner = new DummyExtension(calls, "inner", Collections.EMPTY_LIST);

    @RegisterExtension
    static ChainedExtension tested = ChainedExtension.outer(wDummyOuter).append(wDummyInner).register();

    @Test
    void should_order_extensions(Dummy dummy) {
        Assertions.assertThat(dummy.foo).isEqualTo("off");
        Assertions.assertThat(dummy.bar).isEqualTo("rab");

        Assertions.assertThat(calls).containsExactly(
                "outer_beforeAll",
                "inner_beforeAll",
                "outer_beforeEach",
                "inner_beforeEach",
                "outer_supportsParameter",
                "outer_resolveParameter");
    }

    @AfterAll
    static void tearDown() {
        Assertions.assertThat(calls).containsExactly(
                "outer_beforeAll",
                "inner_beforeAll",
                "outer_beforeEach",
                "inner_beforeEach",
                "outer_supportsParameter",
                "outer_resolveParameter",
                "inner_afterEach",
                "outer_afterEach");
    }
}