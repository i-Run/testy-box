package fr.irun.testy.beat.extensions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Delivery;
import com.rabbitmq.client.Envelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.ReceiverOptions;
import reactor.rabbitmq.SenderOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class WithRabbitEmitterMockTest {

    private static final String QUEUE_NAME = "toto";
    private static final String QUEUE_EXCHANGE = "tata";
    private static final String RETURN_MESSAGE = "returnMessage";
    private static final String MESSAGE_TO_SEND = "totoMessage";
    @RegisterExtension
    static WithRabbitEmitterMock withRabbitEmitterMock = WithRabbitEmitterMock.builder()
            .declareQueueAndExchange(QUEUE_NAME, QUEUE_EXCHANGE)
            .declareSupplier(() -> "ID1" + Math.random())
            .build();
    private static ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp(Channel channel, SenderOptions sender, ReceiverOptions receiver) {
        assertThat(channel).isInstanceOf(Channel.class);
        assertThat(sender).isInstanceOf(SenderOptions.class);
        assertThat(receiver).isInstanceOf(ReceiverOptions.class);
        assertThat(channel).isNotNull();
        assertThat(sender).isNotNull();
        assertThat(receiver).isNotNull();
    }

    @Test
    void emitWithReply(Channel channel, SenderOptions sender) throws IOException {

        List<Delivery> messages = new ArrayList<>();

        channel.basicConsume(QUEUE_NAME, true,
                new DefaultConsumer(channel) {
                    @Override
                    public void handleDelivery(String consumerTag,
                                               Envelope envelope,
                                               AMQP.BasicProperties properties,
                                               byte[] body) throws IOException {
                        messages.add(new Delivery(envelope, properties, body));
                        ObjectMapper objectMapper = new ObjectMapper();
                        channel.basicPublish(
                                "",
                                properties.getReplyTo(),
                                new AMQP.BasicProperties.Builder().correlationId(properties.getCorrelationId()).build(),
                                objectMapper.writeValueAsBytes(RETURN_MESSAGE));
                    }
                });

        assertThat(Objects.requireNonNull(withRabbitEmitterMock.emitWithReply(MESSAGE_TO_SEND, sender)
                .flatMap(delivery -> Mono.fromCallable(() -> objectMapper.readValue(delivery.getBody(), String.class)))
                .block())).isEqualTo(RETURN_MESSAGE);

        assertThat(objectMapper.readValue(messages.get(0).getBody(), String.class)).isEqualTo(MESSAGE_TO_SEND);
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

}