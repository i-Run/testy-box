package fr.ght1pc9kc.testy.beat.messaging;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Delivery;
import com.rabbitmq.client.Envelope;
import fr.ght1pc9kc.testy.beat.extensions.WithRabbitMock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class MockedSenderTest {

    private static final String QUEUE = "test-queue";
    private static final String EXCHANGE = "test-exchange";
    private static final Charset ENCODING = StandardCharsets.UTF_8;
    private static final Duration TIMEOUT = Duration.ofMillis(400);

    @RegisterExtension
    static final WithRabbitMock wRabbitMock = WithRabbitMock.builder()
            .declareQueueAndExchange(QUEUE, EXCHANGE)
            .build();

    @Test
    void should_send_basic_request(Channel channel) throws IOException {
        final TestConsumer consumer = new TestConsumer(channel);
        channel.basicConsume(QUEUE, true, consumer);

        final String request = "test-request";
        final String headerKey = "status";
        final int headerValue = 200;

        final MockedSender tested = new MockedSender(channel);
        final AmqpMessage message = AmqpMessage.builder()
                .header(headerKey, headerValue)
                .body(request.getBytes(ENCODING))
                .build();
        tested.basicPublish(message).on(EXCHANGE, "");

        final Delivery actualRequest = consumer.getRequest().block(TIMEOUT);
        assertThat(actualRequest).isNotNull();
        assertThat(actualRequest.getProperties()).isNotNull();
        assertThat(actualRequest.getProperties().getHeaders()).containsEntry(headerKey, headerValue);

        final byte[] actualBody = actualRequest.getBody();
        assertThat(actualBody).isNotEmpty();
        assertThat(new String(actualBody, ENCODING)).isEqualTo(request);
    }

    @Test
    void should_send_rpc_request(Channel channel) throws IOException {
        final TestConsumer consumer = new TestConsumer(channel);
        channel.basicConsume(QUEUE, true, consumer);

        final String request = "test-request";
        final String headerKey = "status";
        final int headerValue = 200;

        final MockedSender tested = new MockedSender(channel);
        final AmqpMessage message = AmqpMessage.builder()
                .header(headerKey, headerValue)
                .body(request.getBytes(ENCODING))
                .build();

        final Mono<Delivery> actualResponse = tested.rpc(message).on(EXCHANGE, "");
        assertThat(actualResponse.map(Delivery::getBody).map(b -> new String(b, ENCODING)).block(TIMEOUT))
                .isEqualTo(TestConsumer.RESPONSE);

        final Delivery actualRequest = consumer.getRequest().block(TIMEOUT);
        assertThat(actualRequest).isNotNull();
        assertThat(actualRequest.getProperties()).isNotNull();
        assertThat(actualRequest.getProperties().getHeaders()).containsEntry(headerKey, headerValue);

        final byte[] actualBody = actualRequest.getBody();
        assertThat(actualBody).isNotEmpty();
        assertThat(new String(actualBody, ENCODING)).isEqualTo(request);
    }

    private static final class TestConsumer extends DefaultConsumer {

        static final String RESPONSE = "test-response";

        private final AtomicBoolean hasRequest = new AtomicBoolean();
        private final Sinks.One<Delivery> request = Sinks.one();

        public TestConsumer(Channel channel) {
            super(channel);
        }

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
            if (!hasRequest.getAndSet(true)) {
                request.tryEmitValue(new Delivery(envelope, properties, body));

                if (properties.getCorrelationId() != null && properties.getReplyTo() != null) {
                    final AMQP.BasicProperties responseProperties = new AMQP.BasicProperties.Builder()
                            .correlationId(properties.getCorrelationId())
                            .build();

                    getChannel().basicPublish("", properties.getReplyTo(), responseProperties, RESPONSE.getBytes(ENCODING));
                }
            }
        }

        Mono<Delivery> getRequest() {
            return request.asMono();
        }
    }

}