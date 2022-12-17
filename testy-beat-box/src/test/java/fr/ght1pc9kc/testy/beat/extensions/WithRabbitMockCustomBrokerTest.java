package fr.ght1pc9kc.testy.beat.extensions;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Delivery;
import fr.ght1pc9kc.testy.beat.brokers.EmbeddedBroker;
import fr.ght1pc9kc.testy.beat.brokers.QpidEmbeddedBroker;
import fr.ght1pc9kc.testy.beat.messaging.AMQPHelper;
import fr.ght1pc9kc.testy.beat.messaging.AmqpMessage;
import fr.ght1pc9kc.testy.beat.messaging.MockedReceiver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import reactor.core.publisher.Flux;
import reactor.rabbitmq.SenderOptions;
import reactor.test.StepVerifier;

import static fr.ght1pc9kc.testy.beat.utils.DeliveryMappingHelper.readDeliveryValue;
import static org.assertj.core.api.Assertions.assertThat;

class WithRabbitMockCustomBrokerTest {

    private static final String QUEUE_NAME = "queueName";
    private static final String EXCHANGE_NAME = "exchangeName";

    private static final EmbeddedBroker EMBEDDED_BROKER = new QpidEmbeddedBroker(
            "test-broker.json",
            "localhost",
            9596,
            "anakin",
            "skywalker"
    );

    @RegisterExtension
    @SuppressWarnings("unused")
    static final WithRabbitMock WITH_RABBIT_MOCK = WithRabbitMock.builder()
            .withEmbeddedBroker(EMBEDDED_BROKER)
            .declareQueueAndExchange(QUEUE_NAME, EXCHANGE_NAME)
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void should_emit_and_reply_on_custom_broker(
            SenderOptions sender, MockedReceiver receiver) throws JsonProcessingException {
        final String request = "A stupid question?";
        final String response = "A more stupid answer.";

        final byte[] respMessage = objectMapper.writeValueAsBytes("A more stupid answer.");

        Flux<Delivery> actual = receiver.consumeOne().on(QUEUE_NAME)
                .thenRespond(AmqpMessage.of(respMessage))
                .start();

        StepVerifier.create(AMQPHelper.emitWithReply(request, sender, EXCHANGE_NAME)
                        .map(d -> readDeliveryValue(d, objectMapper, String.class)))
                .assertNext(actualResponse -> assertThat(actualResponse)
                        .isNotNull()
                        .isEqualTo(response))
                .verifyComplete();

        StepVerifier.create(actual)
                .assertNext(d -> assertThat(readDeliveryValue(d, objectMapper, String.class)).contains(request))
                .verifyComplete();
    }
}