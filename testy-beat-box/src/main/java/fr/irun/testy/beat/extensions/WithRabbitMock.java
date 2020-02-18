package fr.irun.testy.beat.extensions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Delivery;
import fr.irun.testy.beat.brokers.EmbeddedBroker;
import fr.irun.testy.beat.mappers.DeliveryMapper;
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
import java.io.IOException;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

import static fr.irun.testy.beat.messaging.AMQPHelper.declareAndBindQueues;
import static fr.irun.testy.beat.messaging.AMQPHelper.declareConsumer;
import static fr.irun.testy.beat.messaging.AMQPHelper.declareReceiverOptions;
import static fr.irun.testy.beat.messaging.AMQPHelper.declareReplyQueue;
import static fr.irun.testy.beat.messaging.AMQPHelper.declareSenderOptions;

/**
 * Allow getting a Mock of a Rabbit channel in Tests. Building also Sender and Receiver Options.
 * <p>
 * Usage :
 * <pre style="code">
 *     {@literal @}RegisterExtension
 *     WithRabbitMock wRabbitMock = WithRabbitMock.builder()
 *             .declareQueueAndExchange("queue-name", "exchange-queue-name")
 *             .declareReplyMessage("Reception OK. Reply Message !")
 *             .build();
 * </pre>
 * <p>
 * Usage without reply message (return null as reply message) :
 * <pre style="code">
 *     {@literal @}RegisterExtension
 *     WithRabbitMock wRabbitMock = WithRabbitMock.builder()
 *             .declareQueueAndExchange("queue-name", "exchange-queue-name")
 *             .build();
 * </pre>
 * <p>
 * Assert example to test a "listener" class :
 * <pre style="code">
 *     {@literal @}Test
 *     void test_class_communication(SenderOptions senderOptions) {
 *          Supplier&lt;String&gt; idGenerator = () -&gt; "ID" + Math.random();
 *          tested.subscribe();
 *
 *          assertThat(AMQPHelper.emitWithReply("message to send to tested", senderOptions, "exchange-queue-name", idGenerator)
 *                 .flatMap(delivery -&gt; Mono.fromCallable(() -&gt; objectMapper.readValue(delivery.getBody(), String.class))).block())
 *                 .isEqualTo("message received from tested");
 *      }
 * </pre>
 * <p>
 * Assert example to test a "sender / emitter" class :
 * <pre style="code">
 *     {@literal @}Test
 *     void test_class_communication(Queue&lt;Delivery&gt; messagesReceived) throws IOException {
 *
 *         tested.execute();
 *
 *         assertThat(objectMapper.readValue(messagesReceived.remove().getBody(), String.class))
 *                 .isEqualTo("message sent by tested");
 *     }
 *
 *     //With a custom consumer
 *     {@literal @}Test
 *     void test_class_communication(Channel channel) throws IOException {
 *         Queue&lt;Delivery&gt; messagesReceived = new ArrayBlockingQueue&lt;&gt;(QUEUE_CAPACITY);
 *         AMQPHelper.declareConsumer(channel, messagesReceived, "queue-name", "message received");
 *
 *         tested.execute();
 *
 *         assertThat(objectMapper.readValue(messagesReceived.remove().getBody(), String.class))
 *                 .isEqualTo("message sent by tested");
 *     }
 * </pre>
 */
public final class WithRabbitMock implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback, ParameterResolver {
    private static final int QUEUE_CAPACITY = 10;
    private static final String P_RABBIT_CONNECTION = "rabbit-connection";
    private static final String P_RABBIT_CHANNEL = "rabbit-channel";
    private static final String P_RABBIT_SENDER_OPT = "rabbit-sender-opt";
    private static final String P_RABBIT_RECEIVER_OPT = "rabbit-receiver-opt";
    private static final String P_RABBIT_CONSUMED_QUEUE = "rabbit-consumed-queue";

    private static final Scheduler SCHEDULER = Schedulers.elastic();

    private final EmbeddedBroker embeddedBroker;
    @Nullable
    private final String queueName;
    @Nullable
    private final String exchangeQueueName;
    private final DeliveryMapper<?> responseMapper;
    @Nullable
    private final WithObjectMapper withObjectMapper;

    private WithRabbitMock(@Nullable String queueName,
                           @Nullable String exchangeQueueName,
                           DeliveryMapper<?> responseMapper,
                           @Nullable WithObjectMapper withObjectMapper) {
        this.embeddedBroker = new EmbeddedBroker();
        this.queueName = queueName;
        this.exchangeQueueName = exchangeQueueName;
        this.responseMapper = responseMapper;
        this.withObjectMapper = withObjectMapper;
    }

    public static WithRabbitMockBuilder builder() {
        return new WithRabbitMockBuilder();
    }

    @Override
    public void beforeEach(ExtensionContext context) throws IOException {
        Connection conn = embeddedBroker.newConnection();
        Channel channel = conn.createChannel();

        Queue<Delivery> messages = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        if (queueName != null && exchangeQueueName != null) {
            final ObjectMapper objectMapper = Optional.ofNullable(withObjectMapper)
                    .map(wom -> wom.getObjectMapper(context))
                    .orElseGet(ObjectMapper::new);

            declareAndBindQueues(channel, queueName, exchangeQueueName);
            declareConsumer(channel, objectMapper, messages, queueName, responseMapper);
        }
        declareReplyQueue(channel);

        SenderOptions senderOptions = declareSenderOptions(conn, channel, SCHEDULER);
        ReceiverOptions receiverOptions = declareReceiverOptions(conn, SCHEDULER);

        Store store = getStore(context);
        store.put(P_RABBIT_CONNECTION, conn);
        store.put(P_RABBIT_CHANNEL, channel);
        store.put(P_RABBIT_SENDER_OPT, senderOptions);
        store.put(P_RABBIT_RECEIVER_OPT, receiverOptions);
        store.put(P_RABBIT_CONSUMED_QUEUE, messages);
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        final Channel rabbitChannel = getRabbitChannel(extensionContext);
        if (rabbitChannel.isOpen()) {
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
                || aClass.equals(Queue.class);
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
        if (Queue.class.equals(aClass)) {
            return getStore(extensionContext).get(P_RABBIT_CONSUMED_QUEUE, Queue.class);
        }
        throw new ParameterResolutionException("Unable to resolve parameter for Rabbit Channel !");
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

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        this.embeddedBroker.start();
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        this.embeddedBroker.stop();
    }

    /**
     * Allow to build a Channel rabbit
     */
    public static class WithRabbitMockBuilder {
        private static final DeliveryMapper<?> DEFAULT_DELIVERY_MAPPER = (x -> null);

        private String queueName;
        private String exchangeQueueName;
        private DeliveryMapper<?> responseMapper = DEFAULT_DELIVERY_MAPPER;
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
            this.queueName = queueName;
            this.exchangeQueueName = exchangeQueueName;
            return this;
        }

        /**
         * Declare the message to send as reply for rabbit communication
         *
         * @param replyMessage The message used for reply
         * @return the builder
         */
        public WithRabbitMockBuilder declareReplyMessage(Object replyMessage) {
            this.responseMapper = (x -> replyMessage);
            return this;
        }

        /**
         * Declare a mapper for the repsonse.
         *
         * @param responseMapper Mapper used to convert the request delivery to response object
         * @return Builder instance.
         */
        public WithRabbitMockBuilder declareResponseMapper(DeliveryMapper<?> responseMapper) {
            this.responseMapper = responseMapper;
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
            return new WithRabbitMock(queueName, exchangeQueueName, responseMapper, withObjectMapper);
        }
    }
}
