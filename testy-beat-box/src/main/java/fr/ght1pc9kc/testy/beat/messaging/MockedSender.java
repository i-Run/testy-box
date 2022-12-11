package fr.ght1pc9kc.testy.beat.messaging;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Delivery;
import fr.ght1pc9kc.testy.beat.extensions.WithRabbitMock;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.RpcClient;

import java.io.IOException;
import java.util.UUID;

/**
 * Mocked sender injectable by the extension {@link WithRabbitMock}.
 * This sender allows publishing AMQP messages and RPC requests.
 * <p>
 * Usage:
 * <pre style="code">
 * {@literal @}Test
 * void my_test(MockedSender mockedSender) {
 *     final String request = "test-request";
 *     final AmqpMessage message = AmqpMessage.of(request.getBytes());
 *
 *     mockedSender.basicPublish(message).on("my-exchange", "my-routing-key");
 *
 *     // Verify the tested consumer
 * }
 * </pre>
 * <p>
 * RPC requests can also be sent:
 * <pre style="code">
 * {@literal @}Test
 * void my_test(MockedSender mockedSender) {
 *     final String request = "test-request";
 *     final AmqpMessage message = AmqpMessage.of(request.getBytes());
 *
 *     final Mono&lt;Delivery&gt; actualResponse = mockedSender.rpc(message).on("my-exchange", "my-routing-key");
 *
 *     // Verify the tested consumer and the response
 * }
 * </pre>
 */
public class MockedSender {

    private final Channel channel;

    /**
     * Create the sender.
     *
     * @param channel Channel where the messages are published.
     */
    public MockedSender(Channel channel) {
        this.channel = channel;
    }

    /**
     * Basically publish a message on an exchange.
     *
     * @param message message to publish.
     * @return {@link BasicPublisher}.
     */
    public BasicPublisher basicPublish(AmqpMessage message) {
        return new BasicPublisher(this.channel, message);
    }

    /**
     * Send a RPC request with given message.
     *
     * @param message Message to send.
     * @return {@link RpcPublisher}.
     */
    public RpcPublisher rpc(AmqpMessage message) {
        return new RpcPublisher(this.channel, message);
    }

    /**
     * Publisher build from {@link MockedSender} to simply publish an AMQP message.
     */
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class BasicPublisher {
        private final Channel channel;
        private final AmqpMessage message;

        /**
         * Basic publish the message on given exchange with given routing key.
         *
         * @param exchange   Exchange name.
         * @param routingKey Routing key.
         */
        public void on(String exchange, String routingKey) {
            final AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                    .headers(message.headers)
                    .build();
            try {
                this.channel.basicPublish(exchange, routingKey, properties, message.body);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /**
     * Publisher created by {@link MockedSender} to publish a RPC request.
     */
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class RpcPublisher {
        private final Channel channel;
        private final AmqpMessage message;

        /**
         * Send a RPC request on the given exchange with given routing key.
         *
         * @param exchange   Exchange name.
         * @param routingKey Routing key.
         * @return Non-blocking response.
         */
        public Mono<Delivery> on(String exchange, String routingKey) {
            final AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                    .headers(message.headers)
                    .build();

            final RpcClient.RpcRequest request = new RpcClient.RpcRequest(properties, message.body);
            return Mono.just(new RpcClient(Mono.just(channel), exchange, routingKey, () -> UUID.randomUUID().toString()))
                    .flatMap(c -> c.rpc(Mono.just(request)));
        }

    }

}
