package fr.ght1pc9kc.testy.beat.messaging;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Delivery;
import fr.ght1pc9kc.testy.beat.extensions.WithRabbitMock;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Receiver injectable by the extension {@link WithRabbitMock}.
 * <p>
 * Usage:
 * <pre style="code">
 * {@literal @}Test
 * void my_test(MockedReceiver mockedReceiver, Channel channel) {
 *     final Flux&lt;Delivery&gt; receivedMessages = mockedReceiver.consumeOne().on("my-queue").start();
 *
 *     // Send message
 *     final String message = "test-message";
 *     channel.basicPublish("", "my-queue", null, message.getBytes());
 *
 *     // Obtain the messages sent on the queue
 *     final String actualMessage = receivedMessages.map(d -&gt; new String(d.getBody()))
 *             .single()
 *             .block();
 *     Assertions.assertThat(actualMessage).isEqualTo(message);
 * }
 * </pre>
 * <p>
 * The number of consumed messages can be parameterized.
 * The Flux returned by {@link MockedConsumerBuilder#start()} only completes when the expected number of messages is reached.
 * <pre style="code">
 * {@literal @}Test
 * void my_test(MockedReceiver mockedReceiver, Channel channel) throws IOException {
 *     final String[] messages = {"test-message-1", "test-message-2"};
 *     final Flux&lt;Delivery&gt; receivedMessages = mockedReceiver.consume(2).on("my-queue").start();
 *
 *     // Send message
 *     channel.basicPublish("", "my-queue", null, messages[0].getBytes());
 *     channel.basicPublish("", "my-queue", null, messages[1].getBytes());
 *
 *     // Obtain the messages sent on the queue
 *     final List&lt;String&gt; actualMessages = receivedMessages.map(d -&gt; new String(d.getBody()))
 *             .collectList()
 *             .block();
 *     Assertions.assertThat(actualMessages).containsExactly(messages);
 * }
 * </pre>
 * <p>
 * Responses of the receiver can be defined with model {@link AmqpMessage}.
 * If there are more responses than requests, the last response is replied indefinitely.
 * <pre style="code">
 * {@literal @}Test
 * void my_test(MockedReceiver mockedReceiver, Channel channel) throws IOException {
 *     final String expectedRequest = "test-request";
 *     final String expectedResponse = "test-response";
 *
 *     final Flux&lt;Delivery&gt; receivedMessages = mockedReceiver.consumeOne().on("my-queue")
 *             .thenRespond(AmqpMessage.of(expectedResponse.getBytes()))
 *             .start();
 *
 *     final RpcClient rpcClient = new RpcClient(Mono.just(channel), "my-exchange", "", () -&gt; UUID.randomUUID().toString());
 *
 *     // Send RPC request
 *     final Delivery response = rpcClient.rpc(Mono.just(new RpcClient.RpcRequest(expectedRequest.getBytes())))
 *             .block();
 *     Assertions.assertThat(response).isNotNull();
 *     Assertions.assertThat(response.getBody()).isNotEmpty();
 *     Assertions.assertThat(new String(response.getBody())).isEqualTo(expectedResponse);
 *
 *     // Obtain the messages sent on the queue
 *     final String actualRequest = receivedMessages.map(d -&gt; new String(d.getBody()))
 *             .single()
 *             .block();
 *     Assertions.assertThat(actualRequest).isEqualTo(expectedRequest);
 * }
 * </pre>
 */
public final class MockedReceiver {

    private final Channel channel;

    /**
     * Constructor.
     *
     * @param channel Channel used to consume the messages.
     */
    public MockedReceiver(Channel channel) {
        this.channel = channel;
    }

    /**
     * Define the number of requests to consume on the queue.
     *
     * @param nbRequests Number of requests to consume on the queue (min. 1).
     * @return {@link FixedRequestsConsumerBuilder}.
     */
    public FixedRequestsConsumerBuilder consume(int nbRequests) {
        if (nbRequests < 1) {
            throw new IllegalArgumentException("Expect at least 1 request to be consumed");
        }
        return new FixedRequestsConsumerBuilder(channel, nbRequests);
    }

    /**
     * Consume on request on a queue.
     *
     * @return {@link FixedRequestsConsumerBuilder}.
     */
    public FixedRequestsConsumerBuilder consumeOne() {
        return consume(1);
    }

    /**
     * Intermediate builder for mocked consumer.
     */
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class FixedRequestsConsumerBuilder {
        private final Channel channel;
        private final int nbRequests;

        /**
         * Define the queue on which the messages are consumed.
         *
         * @param queue The queue on which the messages are consumed.
         * @return {@link MockedConsumerBuilder}.
         */
        public MockedConsumerBuilder on(String queue) {
            return new MockedConsumerBuilder(channel, nbRequests, queue);
        }
    }

    /**
     * Intermediate builder for mocked consumer.
     */
    public static final class MockedConsumerBuilder {
        private final Channel channel;
        private final int nbRequests;
        private final String queue;
        private final Queue<AmqpMessage> responses;

        private MockedConsumerBuilder(Channel channel, int nbRequests, String queue) {
            this.channel = channel;
            this.nbRequests = nbRequests;
            this.queue = queue;
            this.responses = new ArrayBlockingQueue<>(nbRequests);
        }

        /**
         * Define the next respond for a request.
         *
         * @param response Next response to reply for a request.
         * @return Builder instance.
         */
        public MockedConsumerBuilder thenRespond(AmqpMessage response) {
            this.responses.offer(response);
            return this;
        }

        /**
         * Start consuming the given number of requests on the given queue.
         *
         * @return Flux of received requests. This flux is completed when the number of requests is reached.
         */
        public Flux<Delivery> start() {
            final MockedConsumer consumer = new MockedConsumer(channel, nbRequests, responses);
            try {
                channel.basicConsume(queue, true, consumer);
                return consumer.getReceivedRequests();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }


}
