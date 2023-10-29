package fr.ght1pc9kc.testy.params.aggregators;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.aggregator.AggregateWith;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class StringVargsAggregatorTest {

    @ParameterizedTest
    @CsvSource({
            "1, one, two, three"
    })
    void should_use_string_aggregator(int index, @AggregateWith(StringVargsAggregator.class) String... aggregated) {
        assertThat(index).isEqualTo(1);
        assertThat(aggregated).containsExactly("one", "two", "three");
    }
}