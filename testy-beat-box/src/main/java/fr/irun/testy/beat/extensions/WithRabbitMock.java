package fr.irun.testy.beat.extensions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import fr.irun.testy.beat.brokers.QpidEmbeddedBroker;
import fr.irun.testy.beat.messaging.AMQPReceiver;
import fr.irun.testy.core.extensions.WithObjectMapper;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.rabbitmq.ReceiverOptions;
import reactor.rabbitmq.SenderOptions;

import javax.annotation.Nullable;
import javax.inject.Named;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import static fr.irun.testy.beat.messaging.AMQPHelper.declareAndBindQueues;
import static fr.irun.testy.beat.messaging.AMQPHelper.declareReceiverOptions;
import static fr.irun.testy.beat.messaging.AMQPHelper.declareSenderOptions;
import static fr.irun.testy.beat.messaging.AMQPHelper.deleteReplyQueue;

/**
 * Allow getting a rabbit broker in tests.
 * <ul>
 *     <li>Can be configured with a customized {@link ObjectMapper}</li>
 *     <li>Starts an embedded AMQP broker</li>
 *     <li>Opens an AMQP connection and channel on each test (closes after)</li>
 *     <li>Builds sender and receiver options, injectable as test parameters</li>
 *     <li>Can declare many queues with related exchanges</li>
 *     <li>For each queue, builds an {@link AMQPReceiver}, injectable as test parameter</li>
 * </ul>
 * <p>
 * Usage :
 * <pre style="code">
 *     private static final String QUEUE_1 = "test-queue-1";
 *     private static final String QUEUE_2 = "test-queue-2";
 *     private static final String EXCHANGE_1 = "test-exchange-1";
 *     private static final String EXCHANGE_2 = "test-exchange-2";
 *
 *     private static final WithObjectMapper WITH_OBJECT_MAPPER = WithObjectMapper.builder()
 *             .addModule(new com.fasterxml.jackson.module.paramnames.ParameterNamesModule())
 *             .build();
 *     private static final WithRabbitMock WITH_RABBIT = WithRabbitMock.builder()
 *             .withObjectMapper(WITH_OBJECT_MAPPER)
 *             .declareQueueAndExchange(QUEUE_1, EXCHANGE_1)
 *             .declareQueueAndExchange(QUEUE_2, EXCHANGE_2)
 *             .build();
 *     {@literal @}RegisterExtension
 *     {@literal @}SuppressWarnings("unused")
 *     static final ChainedExtension CHAIN = ChainedExtension.outer(WITH_OBJECT_MAPPER)
 *             .append(WITH_RABBIT)
 *             .register();
 * </pre>
 * <p>
 * Example to test a "listener" class.
 * A "listener" is expected to:
 * <ul>
 *     <li>Declare the queue and exchange</li>
 *     <li>Consume the messages from the queue</li>
 *     <li>Apply a treatment on the message (specific to each listener)</li>
 *     <li>Reply another message on the reply queue</li>
 * </ul>
 * <p>
 * Note that the request and response can be customized objects, serialized by the input {@link ObjectMapper}.
 * <pre style="code">
 *     {@literal @}Test
 *     void should_consume_queue_and_reply_message(SenderOptions senderOptions, ObjectMapper objectMapper) {
 *          // Here the tested listener declare and consumes QUEUE_1
 *          tested.subscribe();
 *
 *          final String request = "message sent to tested";
 *          final String actualResponse = AMQPHelper.emitWithReply(request, senderOptions, objectMapper, EXCHANGE_1)
 *                  .flatMap(delivery -&gt; Mono.fromCallable(() -&gt; objectMapper.readValue(delivery.getBody(), String.class)))
 *                  .block();
 *          assertThat(actualResponse).isEqualTo("expected message replied by tested listener");
 *      }
 * </pre>
 * <p>
 * Assert example to test an "emitter" class.
 * An "emitter" is expected to:
 * <ul>
 *     <li>Send a message on the queue/exchange</li>
 *     <li>Treat the response.</li>
 * </ul>
 * <p>
 * Note that:
 * <ul>
 *     <li>An {@link AMQPReceiver} can be injected to the test, consuming on the queue</li>
 *     <li>This receiver listens to the queue and sends reply with {@link AMQPReceiver#consumeAndReply(Object)}</li>
 *     <li>This receiver provides the messages pushed into the queue by the tested instance with
 *     {@link AMQPReceiver#getMessages()} and {@link AMQPReceiver#getNextMessage()}</li>
 *     <li>If more than one queue has been declared into {@link WithRabbitMock},
 *     use the annotation {@link Named} to discriminate the {@link AMQPReceiver} parameters</li>
 *     <li>The request and response can be customized objects, serialized by the input {@link ObjectMapper}.</li>
 * </ul>
 * <p>
 * <pre style="code">
 *     {@literal @}Test
 *     void should_emit_message_and_manage_response(@javax.inject.Named(QUEUE_1) AMQPReceiver receiver,
 *                                                  ObjectMapper objectMapper) throws IOException {
 *         final String response = "response from receiver";
 *         receiver.consumeAndReply(response);
 *
 *         tested.execute();
 *
 *         final List&lt;String&gt; actualEmittedMessages = receiver.getMessages()
 *                 .map(delivery &gt; {
 *                     try {
 *                         return objectMapper.readValue(delivery.getBody(), String.class);
 *                     } catch (IOException e) {
 *                         throw new IllegalStateException(e);
 *                     }
 *                 }).collect(Collectors.toList());
 *         assertThat(actualEmittedMessages).containsExactly("message sent by tested");
 *     }
 *
 *     // Test when an error is replied
 *     {@literal @}Test
 *     void should_fail_if_listener_answered_with_error(@javax.inject.Named(QUEUE_1) AMQPReceiver receiver,
 *                                                      ObjectMapper objectMapper) throws IOException {
 *         final Throwable error = new Exception("Mocked receiver error");
 *         receiver.consumeAndReply(error);
 *
 *         assertThatThrownBy(tested::execute)
 *                 .isInstanceOf(MyCustomException.class);
 *
 *         final List&lt;String&gt; actualEmittedMessages = receiver.getMessages()
 *                 .map(delivery &gt; {
 *                     try {
 *                         return objectMapper.readValue(delivery.getBody(), String.class);
 *                     } catch (IOException e) {
 *                         throw new IllegalStateException(e);
 *                     }
 *                 }).collect(Collectors.toList());
 *         assertThat(actualEmittedMessages).containsExactly("message sent by tested");
 *     }
 * </pre>
 */
public final class WithRabbitMock implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback, ParameterResolver {
    private static final String P_RABBIT_CONNECTION = "rabbit-connection";
    private static final String P_RABBIT_CHANNEL = "rabbit-channel";
    private static final String P_RABBIT_SENDER_OPT = "rabbit-sender-opt";
    private static final String P_RABBIT_RECEIVER_OPT = "rabbit-receiver-opt";
    private static final String P_RABBIT_AMQP_RECEIVER_PREFIX = "rabbit-amqp-receiver-";

    private static final Scheduler SCHEDULER = Schedulers.elastic();

    private final QpidEmbeddedBroker embeddedBroker;
    private final Map<String, String> queuesAndExchanges;
    @Nullable
    private final WithObjectMapper withObjectMapper;

    private WithRabbitMock(Map<String, String> queuesAndExchanges,
                           @Nullable WithObjectMapper withObjectMapper) {
        this.embeddedBroker = new QpidEmbeddedBroker();
        this.queuesAndExchanges = queuesAndExchanges;
        this.withObjectMapper = withObjectMapper;
    }

    public static WithRabbitMockBuilder builder() {
        return new WithRabbitMockBuilder();
    }


    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        this.embeddedBroker.start();
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        this.embeddedBroker.stop();
    }

    @Override
    public void beforeEach(ExtensionContext context) throws IOException {
        Store store = getStore(context);

        store.put(P_RABBIT_CONNECTION, embeddedBroker.newConnection());

        Connection conn = getRabbitConnection(context);
        Channel channel = conn.createChannel();

        final ObjectMapper objectMapper = Optional.ofNullable(withObjectMapper)
                .map(wom -> wom.getObjectMapper(context))
                .orElseGet(ObjectMapper::new);

        queuesAndExchanges.forEach((queue, exchange) -> {
            final AMQPReceiver receiver = buildReceiverForQueue(channel, objectMapper, queue, exchange);
            store.put(P_RABBIT_AMQP_RECEIVER_PREFIX + queue, receiver);
        });

        SenderOptions senderOptions = declareSenderOptions(conn, channel, SCHEDULER);
        ReceiverOptions receiverOptions = declareReceiverOptions(conn, SCHEDULER);

        store.put(P_RABBIT_CHANNEL, channel);
        store.put(P_RABBIT_SENDER_OPT, senderOptions);
        store.put(P_RABBIT_RECEIVER_OPT, receiverOptions);
    }

    private AMQPReceiver buildReceiverForQueue(Channel channel, ObjectMapper objectMapper, String queue, String exchange) {
        try {
            declareAndBindQueues(channel, queue, exchange);
            return AMQPReceiver.builder(queue)
                    .objectMapper(objectMapper)
                    .build(channel);
        } catch (IOException e) {
            throw new IllegalStateException("Error when declaring queue " + queue, e);
        }
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        final Channel rabbitChannel = getRabbitChannel(extensionContext);
        if (rabbitChannel.isOpen()) {
            deleteReplyQueue(rabbitChannel);
            rabbitChannel.close();
        }
        final Connection connection = getRabbitConnection(extensionContext);
        if (connection.isOpen()) {
            connection.close();
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Class<?> aClass = parameterContext.getParameter().getType();
        return aClass.equals(Connection.class)
                || aClass.equals(Channel.class)
                || aClass.equals(SenderOptions.class)
                || aClass.equals(ReceiverOptions.class)
                || aClass.equals(AMQPReceiver.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Class<?> aClass = parameterContext.getParameter().getType();
        if (Connection.class.equals(aClass)) {
            return getRabbitConnection(extensionContext);
        }
        if (Channel.class.equals(aClass)) {
            return getRabbitChannel(extensionContext);
        }
        if (SenderOptions.class.equals(aClass)) {
            return getSenderOptions(extensionContext);
        }
        if (ReceiverOptions.class.equals(aClass)) {
            return getReceiverOptions(extensionContext);
        }
        if (AMQPReceiver.class.equals(aClass)) {
            final String queueName = getQueueNameForParameter(parameterContext);
            return getReceiver(extensionContext, queueName);
        }
        throw new ParameterResolutionException("Unable to resolve parameter for Rabbit Channel !");
    }

    private String getQueueNameForParameter(ParameterContext parameterContext) {
        final String queueFromAnnotation = parameterContext.findAnnotation(Named.class)
                .map(Named::value)
                .orElse(null);

        if (queueFromAnnotation == null) {
            if (queuesAndExchanges.size() == 1) {
                return queuesAndExchanges.keySet().iterator().next();
            }
            throw new ParameterResolutionException("Unable to get the queue name for parameter " + AMQPReceiver.class
                    + " - use annotation " + Named.class.getName() + " to provide it");
        }
        return queueFromAnnotation;
    }

    private Store getStore(ExtensionContext context) {
        return context.getStore(ExtensionContext.Namespace.create(getClass()));
    }

    /**
     * Get the Rabbit Channel used for communication
     *
     * @param context The extension context useful for retrieving object into store
     * @return The Channel created
     */
    public Channel getRabbitChannel(ExtensionContext context) {
        return getStore(context).get(P_RABBIT_CHANNEL, Channel.class);
    }

    /**
     * Get the Rabbit connection used for communication
     *
     * @param context The extension context useful for retrieving object into store
     * @return The connection.
     */
    public Connection getRabbitConnection(ExtensionContext context) {
        return getStore(context).get(P_RABBIT_CONNECTION, Connection.class);
    }

    /**
     * Get the Sender Options used for communication
     *
     * @param context The extension context useful for retrieving object into store
     * @return The Sender Options used for channel creation
     */
    public SenderOptions getSenderOptions(ExtensionContext context) {
        return getStore(context).get(P_RABBIT_SENDER_OPT, SenderOptions.class);
    }

    /**
     * Get the Receiver Options used for communication
     *
     * @param context The extension context useful for retrieving object into store
     * @return The Receiver Options used for channel creation
     */
    public ReceiverOptions getReceiverOptions(ExtensionContext context) {
        return getStore(context).get(P_RABBIT_RECEIVER_OPT, ReceiverOptions.class);
    }

    /**
     * Obtain an {@link AMQPReceiver} for the given queue.
     *
     * @param context   The extension context to get objects from the store.
     * @param queueName Name of the queue to get the related receiver.
     * @return {@link AMQPReceiver} related to the given queue.
     */
    public AMQPReceiver getReceiver(ExtensionContext context, String queueName) {
        return getStore(context).get(P_RABBIT_AMQP_RECEIVER_PREFIX + queueName, AMQPReceiver.class);
    }

    /**
     * Allow to build a Channel rabbit
     */
    public static class WithRabbitMockBuilder {

        private final ImmutableMap.Builder<String, String> queuesAndExchanges = ImmutableMap.builder();
        @Nullable
        private WithObjectMapper withObjectMapper;

        /**
         * Declare the queues and exchange for rabbit communication
         *
         * @param queueName         The name of queue for communication
         * @param exchangeQueueName The name of queue for exchange
         * @return the builder
         */
        public WithRabbitMockBuilder declareQueueAndExchange(String queueName, String exchangeQueueName) {
            queuesAndExchanges.put(queueName, exchangeQueueName);
            return this;
        }

        /**
         * Declare an object mapper to convert body to objects.
         *
         * @param withObjectMapper The {@link WithObjectMapper} extension.
         * @return Builder instance.
         */
        public WithRabbitMockBuilder withObjectMapper(WithObjectMapper withObjectMapper) {
            this.withObjectMapper = withObjectMapper;
            return this;
        }

        /**
         * Build the Rabbit Mock junit extension
         *
         * @return The extension
         */
        public WithRabbitMock build() {
            return new WithRabbitMock(queuesAndExchanges.build(), withObjectMapper);
        }
    }
}
