package fr.irun.testy.beat.mappers;

import com.rabbitmq.client.Delivery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SingleValueDeliveryMapperTest {

    private static final String BASE_VALUE = "test-value";

    private Delivery deliveryMock;
    private SingleValueDeliveryMapper<String> tested;

    @BeforeEach
    void setUp() {
        deliveryMock = mock(Delivery.class);
    }

    @Test
    void should_map_to_base_value() {
        tested = new SingleValueDeliveryMapper<>(BASE_VALUE);

        final String actual = tested.map(deliveryMock);
        assertThat(actual).isNotNull();
        assertThat(actual).isEqualTo(BASE_VALUE);
    }

    @Test
    void should_map_to_custom_value() {
        final String customValue = "custom-value";
        tested = new SingleValueDeliveryMapper<>();
        tested.setValue(customValue);

        final String actual = tested.map(deliveryMock);
        assertThat(actual).isNotNull();
        assertThat(actual).isEqualTo(customValue);
    }

    @Test
    void should_map_to_null_if_not_set() {
        tested = new SingleValueDeliveryMapper<>();

        final String actual = tested.map(deliveryMock);
        assertThat(actual).isNull();
    }
}