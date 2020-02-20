package fr.irun.testy.beat.extensions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Delivery;
import fr.irun.testy.beat.messaging.AMQPHelper;
import fr.irun.testy.beat.messaging.AMQPReceiver;
import fr.irun.testy.core.extensions.ChainedExtension;
import fr.irun.testy.core.extensions.WithObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import reactor.rabbitmq.ReceiverOptions;
import reactor.rabbitmq.SenderOptions;

import javax.inject.Named;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test verifying if the expected objects are injected by {@link WithRabbitMock} extension.
 */
class WithRabbitMockTest {

    private static final String QUEUE_1 = "test-queue-1";
    private static final String QUEUE_2 = "test-queue-2";

    private static final String EXCHANGE_1 = "test-exchange-1";
    private static final String EXCHANGE_2 = "test-exchange-2";

    private static final WithObjectMapper WITH_OBJECT_MAPPER = WithObjectMapper.builder()
            .build();
    private static final WithRabbitMock WITH_RABBIT_MOCK = WithRabbitMock.builder()
            .withObjectMapper(WITH_OBJECT_MAPPER)
            .declareQueueAndExchange(QUEUE_1, EXCHANGE_1)
            .declareQueueAndExchange(QUEUE_2, EXCHANGE_2)
            .build();

    @RegisterExtension
    @SuppressWarnings("unused")
    static final ChainedExtension CHAIN = ChainedExtension.outer(WITH_OBJECT_MAPPER)
            .append(WITH_RABBIT_MOCK)
            .register();

    private Function<Delivery, String> deliveryToString;

    @BeforeEach
    void setUp(Connection connection,
               Channel channel,
               SenderOptions sender,
               ReceiverOptions receiver,
               @Named(QUEUE_1) AMQPReceiver receiver1,
               @Named(QUEUE_2) AMQPReceiver receiver2,
               ObjectMapper objectMapper) {

        assertThat(connection).isNotNull();
        assertThat(connection.isOpen()).isTrue();

        assertThat(channel).isNotNull();
        assertThat(channel.isOpen()).isTrue();

        assertThat(sender).isNotNull();
        assertThat(receiver).isNotNull();
        assertThat(receiver1).isNotNull();
        assertThat(receiver1.queueName).isEqualTo(QUEUE_1);
        assertThat(receiver2).isNotNull();
        assertThat(receiver2.queueName).isEqualTo(QUEUE_2);

        this.deliveryToString = d -> {
            try {
                return objectMapper.readValue(d.getBody(), String.class);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        };
    }

    @Test
    void should_inject_connection(Connection tested) {
        assertThat(tested).isNotNull();
        assertThat(tested.isOpen()).isTrue();
    }

    @Test
    void should_inject_channel(Channel tested) {
        assertThat(tested).isNotNull();
        assertThat(tested.isOpen()).isTrue();
    }

    @Test
    void should_inject_sender(SenderOptions tested) {
        assertThat(tested).isNotNull();
    }

    @Test
    void should_inject_receiver(ReceiverOptions tested) {
        assertThat(tested).isNotNull();
    }

    @Test
    void should_inject_amqp_receivers(@Named(QUEUE_1) AMQPReceiver receiver1,
                                      @Named(QUEUE_2) AMQPReceiver receiver2) {
        assertThat(receiver1).isNotNull();
        assertThat(receiver1.queueName).isEqualTo(QUEUE_1);
        assertThat(receiver2).isNotNull();
        assertThat(receiver2.queueName).isEqualTo(QUEUE_2);
    }

    @Test
    void should_emit_with_reply_on_queue_1(SenderOptions sender,
                                           @Named(QUEUE_1) AMQPReceiver receiver) {
        final String request = "obiwan";
        final String response = "kenobi";

        receiver.consumeAndReply(response);

        final String actualResponse = AMQPHelper.emitWithReply(request, sender, EXCHANGE_1)
                .map(deliveryToString)
                .block();
        assertThat(actualResponse).isNotNull();
        assertThat(actualResponse).isEqualTo(response);

        final Optional<String> actualRequest = receiver.getNextMessage().map(deliveryToString);
        assertThat(actualRequest).contains(request);

        assertThat(receiver.getNextMessage()).isEmpty();
    }

    @Test
    void should_emit_with_reply_on_queue_2(SenderOptions sender,
                                           @Named(QUEUE_2) AMQPReceiver receiver) {
        final String request = "anakin";
        final String response = "skywalker";

        receiver.consumeAndReply(response);

        final String actualResponse = AMQPHelper.emitWithReply(request, sender, EXCHANGE_2)
                .map(deliveryToString)
                .block();
        assertThat(actualResponse).isNotNull();
        assertThat(actualResponse).isEqualTo(response);

        final Optional<String> actualRequest = receiver.getNextMessage().map(deliveryToString);
        assertThat(actualRequest).contains(request);

        assertThat(receiver.getNextMessage()).isEmpty();
    }

}