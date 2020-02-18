package fr.irun.testy.beat.extensions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Delivery;
import fr.irun.testy.beat.mappers.DeliveryMapper;
import fr.irun.testy.beat.mappers.JacksonDeliveryMapperFactory;
import fr.irun.testy.beat.messaging.AMQPHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.rabbitmq.SenderOptions;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test case example for a queue consumer (expected to declare itself the queue and exchange).
 */
public class WithRabbitMockConsumptionTest {

    private static final String QUEUE_NAME = "queueName";
    private static final String EXCHANGE_NAME = "exchangeName";

    private static final String REQUEST_1 = "Request obiwan";
    private static final String RESPONSE_1 = "Response kenobi";
    private static final String REQUEST_2 = "Request Anakin";
    private static final String RESPONSE_2 = "Response Skywalker";
    private static final String REQUEST_3 = "An idiot question?";
    private static final String RESPONSE_3 = "A more idiot answer.";

    @RegisterExtension
    @SuppressWarnings("unused")
    static final WithRabbitMock WITH_RABBIT_MOCK = WithRabbitMock.builder()
            .build();

    private SenderOptions senderOptions;
    private ObjectMapper objectMapper;
    private TestQueueConsumer consumer;

    @BeforeEach
    void setUp(Channel channel, SenderOptions senderOptions) {
        this.senderOptions = senderOptions;
        this.objectMapper = new ObjectMapper();
        this.consumer = new TestQueueConsumer(channel, objectMapper);
    }

    private static Stream<Arguments> params_should_consume_emitted_message() {
        return Stream.of(
                Arguments.of(REQUEST_1, RESPONSE_1),
                Arguments.of(REQUEST_2, RESPONSE_2),
                Arguments.of(REQUEST_3, RESPONSE_3)
        );
    }

    @ParameterizedTest
    @MethodSource("params_should_consume_emitted_message")
    void should_consume_emitted_message(String request, String expectedResponse) {
        consumer.subscribe();

        final DeliveryMapper<String> mapper = JacksonDeliveryMapperFactory.forClass(objectMapper, String.class);

        final String actual = AMQPHelper.emitWithReply(request, senderOptions, EXCHANGE_NAME)
                .map(mapper::map)
                .block();
        assertThat(actual).isNotNull();
        assertThat(actual).isEqualTo(expectedResponse);
    }

    private static final class TestQueueConsumer {

        private final Channel channel;
        private final ObjectMapper objectMapper;

        private TestQueueConsumer(Channel channel, ObjectMapper objectMapper) {
            this.channel = channel;
            this.objectMapper = objectMapper;
        }

        public void subscribe() {
            try {
                AMQPHelper.declareAndBindQueues(channel, QUEUE_NAME, EXCHANGE_NAME);
                Queue<Delivery> messages = new ArrayBlockingQueue<>(10);
                AMQPHelper.declareConsumer(channel, objectMapper, messages, QUEUE_NAME, new TestResponseMapper(objectMapper));
            } catch (IOException e) {
                throw new IllegalStateException("Error when subscribing to TestQueueConsumer", e);
            }
        }
    }

    private static final class TestResponseMapper implements DeliveryMapper<String> {

        private final ImmutableMap<String, String> responsesByRequests = ImmutableMap.of(
                REQUEST_1, RESPONSE_1,
                REQUEST_2, RESPONSE_2,
                REQUEST_3, RESPONSE_3
        );

        private final DeliveryMapper<String> requestMapper;

        private TestResponseMapper(ObjectMapper objectMapper) {
            requestMapper = JacksonDeliveryMapperFactory.forClass(objectMapper, String.class);
        }

        @Override
        public String map(Delivery delivery) {
            final String requestBody = requestMapper.map(delivery);
            return responsesByRequests.get(requestBody);
        }
    }


}
