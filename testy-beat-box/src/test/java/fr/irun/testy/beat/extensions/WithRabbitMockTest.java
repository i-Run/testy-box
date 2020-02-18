package fr.irun.testy.beat.extensions;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Delivery;
import fr.irun.testy.beat.mappers.SingleValueDeliveryMapper;
import fr.irun.testy.core.extensions.ChainedExtension;
import fr.irun.testy.core.extensions.WithObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import reactor.rabbitmq.ReceiverOptions;
import reactor.rabbitmq.SenderOptions;

import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test verifying if the expected objects are injected by {@link WithRabbitMock} extension.
 */
class WithRabbitMockTest {

    private static final String RESULT_OK = "Result ok";
    private static final String QUEUE_NAME = "queueName";
    private static final String EXCHANGE_NAME = "exchangeName";

    private static final SingleValueDeliveryMapper<String> RESPONSE_MAPPER = new SingleValueDeliveryMapper<>(RESULT_OK);

    private static final WithObjectMapper WITH_OBJECT_MAPPER = WithObjectMapper.builder()
            .build();
    private static final WithRabbitMock WITH_RABBIT_MOCK = WithRabbitMock.builder()
            .withObjectMapper(WITH_OBJECT_MAPPER)
            .declareQueueAndExchange(QUEUE_NAME, EXCHANGE_NAME)
            .declareResponseMapper(RESPONSE_MAPPER)
            .build();

    @RegisterExtension
    @SuppressWarnings("unused")
    static final ChainedExtension CHAIN = ChainedExtension.outer(WITH_OBJECT_MAPPER)
            .append(WITH_RABBIT_MOCK)
            .register();

    @BeforeEach
    void setUp(Channel channel,
               SenderOptions sender,
               ReceiverOptions receiver,
               Queue<String> messages) {
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
    void should_inject_connection(Connection tested) {
        assertThat(tested).isNotNull();
        assertThat(tested.isOpen()).isTrue();
    }

    @Test
    void should_inject_channel(Channel tested) {
        assertThat(tested).isInstanceOf(Channel.class);
        assertThat(tested).isNotNull();
    }

    @Test
    void should_inject_sender_options(SenderOptions tested) {
        assertThat(tested).isInstanceOf(SenderOptions.class);
        assertThat(tested).isNotNull();
    }

    @Test
    void should_inject_receiver_options(ReceiverOptions tested) {
        assertThat(tested).isInstanceOf(ReceiverOptions.class);
        assertThat(tested).isNotNull();
    }

    @Test
    void should_inject_queue(Queue<Delivery> tested) {
        assertThat(tested).isInstanceOf(Queue.class);
        assertThat(tested).isNotNull();
    }
}