package fr.irun.testy.beat.extensions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Delivery;
import fr.irun.testy.beat.messaging.AMQPHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.ReceiverOptions;
import reactor.rabbitmq.SenderOptions;

import java.io.IOException;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class WithRabbitMockTest {

    private static final String RESULT_OK = "Result ok";
    private static final String QUEUE_NAME = "queueName";
    private static final String EXCHANGE_NAME = "exchangeName";
    private static final String MESSAGE_TO_SEND = "sendThisMessage";
    private static final int QUEUE_CAPACITY = 10;
    private static final Supplier<String> STRING_SUPPLIER = () -> "ID1" + Math.random();

    @RegisterExtension
    static WithRabbitMock withRabbitMock = WithRabbitMock.builder()
            .declareQueueAndExchange(QUEUE_NAME, EXCHANGE_NAME)
            .declareReplyMessage(RESULT_OK)
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
    void should_listen_and_reply(SenderOptions senderOptions, Queue<Delivery> messages) throws IOException {
        Delivery replyMessage = AMQPHelper.emitWithReply(MESSAGE_TO_SEND, senderOptions, EXCHANGE_NAME, STRING_SUPPLIER).block();

        assert replyMessage != null;
        assertThat(objectMapper.readValue(replyMessage.getBody(), String.class))
                .isEqualTo(RESULT_OK);

        assert messages != null;
        assertThat(messages.isEmpty()).isFalse();
        assertThat(objectMapper.readValue(messages.remove().getBody(), String.class))
                .isEqualTo(MESSAGE_TO_SEND);
    }

    @Test
    void emitWithReply(Channel channel, SenderOptions sender) throws IOException {

        Queue<Delivery> messages = new ArrayBlockingQueue<>(QUEUE_CAPACITY);

        AMQPHelper.declareConsumer(channel, messages, QUEUE_NAME, "response");

        assertThat(Objects.requireNonNull(
                AMQPHelper.emitWithReply(MESSAGE_TO_SEND, sender, EXCHANGE_NAME, STRING_SUPPLIER)
                        .flatMap(delivery -> Mono.fromCallable(() -> objectMapper.readValue(delivery.getBody(), String.class)))
                        .block())).isEqualTo("response");

        assertThat(objectMapper.readValue(messages.remove().getBody(), String.class)).isEqualTo(MESSAGE_TO_SEND);
    }
}