package fr.irun.testy.beat.extensions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Delivery;
import fr.irun.testy.beat.messaging.AMQPHelper;
import fr.irun.testy.beat.messaging.AMQPReceiver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import reactor.rabbitmq.SenderOptions;

import java.util.Optional;

import static fr.irun.testy.beat.utils.DeliveryMappingHelper.readDeliveryValue;
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
    void should_emit_and_reply_null(SenderOptions senderOptions, AMQPReceiver receiver) {
        receiver.consumeAndReply(null);

        Delivery actualResponse = AMQPHelper.emitWithReply(MESSAGE_TO_SEND, senderOptions, EXCHANGE_NAME).block();
        assertThat(actualResponse).isNotNull();

        final String actualResponseBody = readDeliveryValue(actualResponse, OBJECT_MAPPER, String.class);
        assertThat(actualResponseBody).isNull();

        final Optional<String> actualMessage = receiver.getNextMessage()
                .map(d -> readDeliveryValue(d, OBJECT_MAPPER, String.class));
        assertThat(actualMessage).contains(MESSAGE_TO_SEND);
    }
}