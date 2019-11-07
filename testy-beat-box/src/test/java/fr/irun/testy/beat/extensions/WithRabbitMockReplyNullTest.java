package fr.irun.testy.beat.extensions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Delivery;
import fr.irun.testy.beat.messaging.AMQPHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import reactor.rabbitmq.ReceiverOptions;
import reactor.rabbitmq.SenderOptions;

import java.io.IOException;
import java.util.Queue;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class WithRabbitMockReplyNullTest {

    private static final String QUEUE_NAME = "queueName";
    private static final String EXCHANGE_NAME = "exchangeName";
    private static final String MESSAGE_TO_SEND = "sendThisMessage";
    private static final Supplier<String> STRING_SUPPLIER = () -> "ID1" + Math.random();

    @RegisterExtension
    static WithRabbitMock withRabbitMock = WithRabbitMock.builder()
            .declareQueueAndExchange(QUEUE_NAME, EXCHANGE_NAME)
            .build();
    private static ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp(Channel channel, SenderOptions sender, ReceiverOptions receiver, Queue<String> messages) {
        assertThat(channel).isInstanceOf(Channel.class);
        assertThat(sender).isInstanceOf(SenderOptions.class);
        assertThat(receiver).isInstanceOf(ReceiverOptions.class);
        assertThat(messages).isInstanceOf(Queue.class);
        assertThat(channel).isNotNull();
        assertThat(sender).isNotNull();
        assertThat(receiver).isNotNull();
        assertThat(messages).isNotNull();
    }

    @Test
    void should_inject_channel(Channel tested) {
        assertThat(tested).isInstanceOf(Channel.class);
        assertThat(tested).isNotNull();
    }

    @Test
    void should_inject_sender(SenderOptions tested) {
        assertThat(tested).isInstanceOf(SenderOptions.class);
        assertThat(tested).isNotNull();
    }

    @Test
    void should_inject_receiver(ReceiverOptions tested) {
        assertThat(tested).isInstanceOf(ReceiverOptions.class);
        assertThat(tested).isNotNull();
    }

    @Test
    void should_inject_queue(Queue<Delivery> tested) {
        assertThat(tested).isInstanceOf(Queue.class);
        assertThat(tested).isNotNull();
    }

    @Test
    void should_listen_and_reply_null(SenderOptions senderOptions, Queue<Delivery> messages) throws IOException {
        Delivery replyMessage = AMQPHelper.emitWithReply(MESSAGE_TO_SEND, senderOptions, EXCHANGE_NAME, STRING_SUPPLIER).block();

        assert replyMessage != null;
        assertThat(objectMapper.readValue(replyMessage.getBody(), String.class))
                .isNull();

        assert messages != null;
        assertThat(messages.isEmpty()).isFalse();
        assertThat(objectMapper.readValue(messages.remove().getBody(), String.class))
                .isEqualTo(MESSAGE_TO_SEND);
    }
}