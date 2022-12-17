package fr.ght1pc9kc.testy.beat.extensions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Delivery;
import fr.ght1pc9kc.testy.beat.messaging.AMQPHelper;
import fr.ght1pc9kc.testy.beat.messaging.AmqpMessage;
import fr.ght1pc9kc.testy.beat.messaging.MockedReceiver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import reactor.core.publisher.Flux;
import reactor.rabbitmq.SenderOptions;
import reactor.test.StepVerifier;

import java.util.Objects;

import static fr.ght1pc9kc.testy.beat.utils.DeliveryMappingHelper.readDeliveryValue;
import static org.assertj.core.api.Assertions.assertThat;

class WithRabbitMockReplyNullTest {

    private static final String QUEUE_NAME = "queueName";
    private static final String EXCHANGE_NAME = "exchangeName";
    private static final String MESSAGE_TO_SEND = "sendThisMessage";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @RegisterExtension
    @SuppressWarnings("unused")
    static final WithRabbitMock WITH_RABBIT_MOCK = WithRabbitMock.builder()
            .declareQueueAndExchange(QUEUE_NAME, EXCHANGE_NAME)
            .build();

    @Test
    void should_emit_and_reply_null(SenderOptions senderOptions, MockedReceiver receiver) throws JsonProcessingException {
        Flux<Delivery> actual = receiver.consumeOne().on(QUEUE_NAME)
                .thenRespond(AmqpMessage.of(OBJECT_MAPPER.writeValueAsBytes(null)))
                .start();

        StepVerifier.create(AMQPHelper.emitWithReply(MESSAGE_TO_SEND, senderOptions, EXCHANGE_NAME))
                .expectNextMatches(delivery -> Objects.isNull(readDeliveryValue(delivery, OBJECT_MAPPER, String.class)))
                .expectComplete()
                .verify();

        StepVerifier.create(actual.map(d -> readDeliveryValue(d, OBJECT_MAPPER, String.class)))
                .assertNext(message -> assertThat(message).contains(MESSAGE_TO_SEND))
                .verifyComplete();
    }
}