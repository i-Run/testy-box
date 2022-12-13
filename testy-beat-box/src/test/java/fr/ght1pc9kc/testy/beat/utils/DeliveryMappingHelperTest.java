package fr.ght1pc9kc.testy.beat.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.google.common.collect.ImmutableList;
import com.rabbitmq.client.Delivery;
import fr.ght1pc9kc.testy.beat.utils.samples.TestModel;
import fr.ght1pc9kc.testy.core.extensions.WithObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeliveryMappingHelperTest {

    @RegisterExtension
    @SuppressWarnings("unused")
    static final WithObjectMapper WITH_OBJECT_MAPPER = WithObjectMapper.builder()
            .addModule(new ParameterNamesModule())
            .build();

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Test
    void should_convert_to_model_with_class() {
        final byte[] body = DeliveryMappingHelper.writeObjectAsByte(TestModel.OBIWAN, objectMapper);

        final Delivery delivery = new Delivery(null, null, body);
        final TestModel actual = DeliveryMappingHelper.readDeliveryValue(delivery, objectMapper, TestModel.class);

        assertThat(actual).isNotNull().isEqualTo(TestModel.OBIWAN);
    }

    @Test
    void should_fail_to_convert_invalid_type_delivery_with_class() {
        final byte[] invalidBody = "invalid-value".getBytes(StandardCharsets.UTF_8);
        final Delivery delivery = new Delivery(null, null, invalidBody);

        assertThatThrownBy(() -> DeliveryMappingHelper.readDeliveryValue(delivery, objectMapper, TestModel.class))
                .isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void should_map_with_type_reference() {
        final TypeReference<List<TestModel>> typeReference = new TypeReference<List<TestModel>>() {
        };

        final byte[] body = DeliveryMappingHelper.writeObjectAsByte(ImmutableList.of(TestModel.OBIWAN, TestModel.ANAKIN), objectMapper);

        final Delivery delivery = new Delivery(null, null, body);
        final List<TestModel> actual = DeliveryMappingHelper.readDeliveryValue(delivery, objectMapper, typeReference);

        assertThat(actual).isNotNull().containsExactly(TestModel.OBIWAN, TestModel.ANAKIN);
    }

    @Test
    void should_fail_to_convert_invalid_type_delivery_with_type_reference() {
        final TypeReference<List<TestModel>> typeReference = new TypeReference<List<TestModel>>() {
        };
        final byte[] invalidBody = "invalid-value".getBytes(StandardCharsets.UTF_8);
        final Delivery delivery = new Delivery(null, null, invalidBody);

        assertThatThrownBy(() -> DeliveryMappingHelper.readDeliveryValue(delivery, objectMapper, typeReference))
                .isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void should_fail_to_map_pojo_to_delivery() throws JsonProcessingException {
        final ObjectMapper objectMapperMock = mock(ObjectMapper.class);

        final JsonProcessingException error = new MockedException("Mocked Error");
        when(objectMapperMock.writeValueAsBytes(any())).thenThrow(error);

        assertThatThrownBy(() -> DeliveryMappingHelper.writeObjectAsByte(TestModel.OBIWAN, objectMapperMock))
                .isInstanceOf(IllegalStateException.class)
                .hasCause(error);
    }

    private static final class MockedException extends JsonProcessingException {
        protected MockedException(String msg) {
            super(msg);
        }
    }
}