package fr.irun.testy.beat.messaging;

import com.rabbitmq.client.Channel;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Class injectable in tests using {@link fr.irun.testy.beat.extensions.WithRabbitMock} extension.
 * The factory is injected at BeforeEach level.
 * <p>
 * Usage:
 * <pre style="code">
 * {@literal @}Test
 * void my_rabbit_test(MockedReceiverFactory receiverFactory, Channel channel) {
 *     final MockedReceiver receiver = receiverFactory.consumeOne().on("my-queue").start();
 *
 *     // Send message
 *     final String message = "test-message";
 *     channel.basicPublish("", "my-queue", null, message.getBytes());
 *
 *     // Obtain the messages sent on the queue
 *     final Flux&lt;Delivery&gt; receivedMessages = receiver.getReceivedMessages();
 *     final String actualMessage = receivedMessages.map(d -&gt; new String(d.getBody()))
 *             .single()
 *             .block();
 *     Assertions.assertThat(actualMessage).isEqualTo(message);
 * }
 * </pre>
 * <p>
 * The number of consumed messages can be parameterized.
 * The Flux returned by {@link MockedReceiver#getReceivedMessages()} only completes when the expected number of messages is reached.
 * <pre style="code">
 * {@literal @}Test
 * void my_rabbit_test(MockedReceiverFactory receiverFactory, Channel channel) throws IOException {
 *     final String[] messages = {"test-message-1", "test-message-2"};
 *     final MockedReceiver receiver = receiverFactory.consume(2).on("my-queue").start();
 *
 *     // Send message
 *     channel.basicPublish("", "my-queue", null, messages[0].getBytes());
 *     channel.basicPublish("", "my-queue", null, messages[1].getBytes());
 *
 *     // Obtain the messages sent on the queue
 *     final Flux&lt;Delivery&gt; receivedMessages = receiver.getReceivedMessages();
 *     final List&lt;String&gt; actualMessages = receivedMessages.map(d -&gt; new String(d.getBody()))
 *             .collectList()
 *             .block();
 *     Assertions.assertThat(actualMessages).containsExactly(messages);
 * }
 * </pre>
 * <p>
 * Some {@link AmqpMessage} can be added to the mock. The responses are returned in the same order as declared.
 * If there are more responses than requests, the last response is replied indefinitely.
 * <pre style="code">
 * {@literal @}Test
 * void my_rabbit_test(MockedReceiverFactory receiverFactory, Channel channel) throws IOException {
 *     final String expectedRequest = "test-request";
 *     final String expectedResponse = "test-response";
 *
 *     final MockedReceiver receiver = receiverFactory.consumeOne().on("my-queue")
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
 *
 *     final String actualResponse = new String(response.getBody());
 *     Assertions.assertThat(actualResponse).isEqualTo(expectedResponse);
 *
 *     // Obtain the messages sent on the queue
 *     final Flux&lt;Delivery&gt; receivedMessages = receiver.getReceivedMessages();
 *     final String actualRequest = receivedMessages.map(d -&gt; new String(d.getBody()))
 *             .single()
 *             .block();
 *     Assertions.assertThat(actualRequest).isEqualTo(expectedRequest);
 * }
 * </pre>
 */
public final class MockedReceiverFactory {

    private final Channel channel;

    /**
     * Create a new factory.
     *
     * @param channel Rabbit channel.
     */
    public MockedReceiverFactory(Channel channel) {
        this.channel = channel;
    }

    /**
     * Obtain the channel related to this factory.
     *
     * @return Channel related to this factory.
     */
    public Channel getChannel() {
        return this.channel;
    }

    /**
     * Consume a given number of requests.
     *
     * @param nbRequests The number of requests to consume.
     * @return {@link MockedReceiverBuilder}.
     */
    public MockedReceiverRequestCountBuilder consume(int nbRequests) {
        return new MockedReceiverRequestCountBuilder(channel, nbRequests);
    }

    /**
     * Consume a single request on the queue.
     *
     * @return {@link MockedReceiverBuilder}.
     */
    public MockedReceiverRequestCountBuilder consumeOne() {
        return consume(1);
    }

    /**
     * Intermediate builder for {@link MockedReceiver}.
     */
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class MockedReceiverRequestCountBuilder {
        private final Channel channel;
        private final int nbRequests;

        /**
         * Define the queue on which the consumption is done.
         *
         * @param queueName Name of the queue where the consumption is done.
         * @return {@link MockedReceiverBuilder}.
         */
        public MockedReceiverBuilder on(String queueName) {
            return new MockedReceiverBuilder(channel, nbRequests, queueName);
        }
    }

    /**
     * Intermediate builder for {@link MockedReceiver}.
     */
    public static final class MockedReceiverBuilder {

        private final Channel channel;
        private final String queueName;
        private final int nbRequests;

        private final Queue<AmqpMessage> responses;

        private MockedReceiverBuilder(Channel channel,
                                      int nbRequests,
                                      String queueName) {
            this.queueName = queueName;
            this.nbRequests = nbRequests;
            this.channel = channel;
            this.responses = new ArrayBlockingQueue<>(nbRequests);
        }

        /**
         * Add a response to the receiver.
         * This response will be send only if the request requires it (i.e. has replyTo queue and correlation ID)
         *
         * @param response The response to add.
         * @return Builder instance.
         */
        public MockedReceiverBuilder thenRespond(AmqpMessage response) {
            this.responses.offer(response);
            return this;
        }

        /**
         * Create a new started {@link MockedReceiver}.
         *
         * @return The started {@link MockedReceiver}.
         */
        public MockedReceiver start() {
            final MockedConsumer consumer = new MockedConsumer(channel, nbRequests, responses);
            final MockedReceiver receiver = new MockedReceiver(channel, queueName, consumer);
            receiver.start();
            return receiver;
        }
    }

}
