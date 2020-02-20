package fr.irun.testy.beat.messaging;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Delivery;
import fr.irun.testy.beat.extensions.WithRabbitMock;
import fr.irun.testy.core.extensions.ChainedExtension;
import fr.irun.testy.core.extensions.WithObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import reactor.rabbitmq.SenderOptions;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class AMQPReceiverTest {

    private static final String QUEUE_NAME = "test-queue";
    private static final String EXCHANGE_NAME = "test-exchange";

    private static final String REQUEST_BODY = "An idiot question?";
    private static final String RESPONSE_BODY = "A more idiot answer.";

    private static final Duration TIMEOUT = Duration.ofSeconds(1);

    private static final WithObjectMapper WITH_OBJECT_MAPPER = WithObjectMapper.builder()
            .build();
    private static final WithRabbitMock WITH_RABBIT_MOCK = WithRabbitMock.builder()
            .withObjectMapper(WITH_OBJECT_MAPPER)
            .build();

    @RegisterExtension
    @SuppressWarnings("unused")
    static final ChainedExtension CHAIN = ChainedExtension.outer(WITH_OBJECT_MAPPER)
            .append(WITH_RABBIT_MOCK)
            .register();

    private SenderOptions senderOptions;
    private ObjectMapper objectMapper;
    private Function<Delivery, String> deliveryToStringMapper;

    private AMQPReceiver tested;

    @BeforeEach
    void setUp(Channel channel, SenderOptions senderOptions, ObjectMapper objectMapper) throws IOException {
        this.senderOptions = senderOptions;
        this.objectMapper = objectMapper;
        this.deliveryToStringMapper = d -> {
            try {
                return objectMapper.readValue(d.getBody(), String.class);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        };
        AMQPHelper.declareAndBindQueues(channel, QUEUE_NAME, EXCHANGE_NAME);

        tested = AMQPReceiver.builder(QUEUE_NAME)
                .objectMapper(objectMapper)
                .build(channel);
    }

    @Test
    void should_get_next_message_after_consumption() {
        tested.consumeAndReply(RESPONSE_BODY);

        final String actualResponse = AMQPHelper.emitWithReply(REQUEST_BODY, objectMapper, senderOptions, QUEUE_NAME, TIMEOUT)
                .map(deliveryToStringMapper)
                .block();
        assertThat(actualResponse).isNotNull();
        assertThat(actualResponse).isEqualTo(RESPONSE_BODY);

        final Optional<String> actualMessage = tested.getNextMessage()
                .map(deliveryToStringMapper);
        assertThat(actualMessage).contains(REQUEST_BODY);

        assertThat(tested.getNextMessage()).isEmpty();
    }
}