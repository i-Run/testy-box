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

    @RegisterExtension
    static WithRabbitEmitterMock withRabbitEmitterMock = WithRabbitEmitterMock.builder()
            .declareQueueAndExchange("toto", "tata")
            .declareSupplier(() -> "ID1" + Math.random())
            .build();

    @BeforeEach
    void setUp(Channel channel, SenderOptions sender, ReceiverOptions receiver) throws IOException {
        assertThat(channel).isInstanceOf(Channel.class);
        assertThat(sender).isInstanceOf(SenderOptions.class);
        assertThat(receiver).isInstanceOf(ReceiverOptions.class);
    }


    @Test
    void emitWithReply(Channel channel, SenderOptions sender) throws IOException {

        List<Delivery> messages = new ArrayList<>();

        channel.basicConsume("toto", true,
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
                                objectMapper.writeValueAsBytes("returnMessage"));
                    }
                });


        ObjectMapper objectMapper = new ObjectMapper();
        assertThat(Objects.requireNonNull(withRabbitEmitterMock.emitWithReply("totoMessage", sender)
                .flatMap(delivery -> Mono.fromCallable(() -> objectMapper.readValue(delivery.getBody(), String.class)))
                .block())).isEqualTo("returnMessage");

        assertThat(objectMapper.readValue(messages.get(0).getBody(), String.class)).isEqualTo("totoMessage");
    }

    @Test
    void should_inject_channel(Channel tested) {
        assertThat(tested).isInstanceOf(Channel.class);
    }

    @Test
    void should_inject_sender(SenderOptions tested) {
        assertThat(tested).isInstanceOf(SenderOptions.class);
    }

    @Test
    void should_inject_receiver(ReceiverOptions tested) {
        assertThat(tested).isInstanceOf(ReceiverOptions.class);
    }
}