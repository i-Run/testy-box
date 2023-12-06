package fr.ght1pc9kc.testy.core.utils;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class PortUtilsTest {
    @Test
    void should_find_free_port() {
        Assertions.assertThat(PortUtils.randomFreePort()).isPositive();
    }
}