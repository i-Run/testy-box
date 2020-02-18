package fr.irun.testy.beat.extensions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Delivery;
import fr.irun.testy.beat.messaging.AMQPHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import reactor.rabbitmq.SenderOptions;

import java.io.IOException;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;

class WithRabbitMockReplyNullTest {

    private static final String QUEUE_NAME = "queueName";
    private static final String EXCHANGE_NAME = "exchangeName";
    private static final String MESSAGE_TO_SEND = "sendThisMessage";

    @RegisterExtension
    @SuppressWarnings("unused")
    static final WithRabbitMock WITH_RABBIT_MOCK = WithRabbitMock.builder()
            .declareQueueAndExchange(QUEUE_NAME, EXCHANGE_NAME)
            .build();

    private static ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void should_emit_and_reply_null(SenderOptions senderOptions, Queue<Delivery> receivedMessages) throws IOException {
        Delivery actualResponse = AMQPHelper.emitWithReply(MESSAGE_TO_SEND, senderOptions, EXCHANGE_NAME).block();
        assertThat(actualResponse).isNotNull();

        final String actualResponseBody = objectMapper.readValue(actualResponse.getBody(), String.class);
        assertThat(actualResponseBody).isNull();

        assertThat(receivedMessages).isNotNull();
        assertThat(receivedMessages).isNotEmpty();
        final String actualReceivedBody = objectMapper.readValue(receivedMessages.remove().getBody(), String.class);
        assertThat(actualReceivedBody).isEqualTo(MESSAGE_TO_SEND);
    }
}