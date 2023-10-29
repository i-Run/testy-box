package fr.ght1pc9kc.testy.params.aggregators;

import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.params.aggregator.ArgumentsAccessor;
import org.junit.jupiter.params.aggregator.ArgumentsAggregationException;
import org.junit.jupiter.params.aggregator.ArgumentsAggregator;

/**
 * Allow to aggregate String Var Args in junit parameterized Test
 *
 * <pre style="java">
 * {@literal @}ParameterizedTest
 * {@literal @}CsvSource({
 *        "1, one, two, three"
 * })
 * void should_use_string_aggregator(int index, @AggregateWith(StringVargsAggregator.class) String... aggregated) {
 *     assertThat(index).isEqualTo(1);
 *     assertThat(aggregated).containsExactly("one", "two", "three");
 * }
 * </pre>
 */
public class StringVargsAggregator implements ArgumentsAggregator {
    @Override
    public Object aggregateArguments(ArgumentsAccessor accessor, ParameterContext context) throws ArgumentsAggregationException {
        return accessor.toList().stream()
                .skip(context.getIndex())
                .map(String::valueOf)
                .toArray(String[]::new);
    }
}
