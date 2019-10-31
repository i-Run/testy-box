package fr.irun.testy.beat.extensions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fridujo.rabbitmq.mock.MockConnectionFactory;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Delivery;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.rabbitmq.RabbitFlux;
import reactor.rabbitmq.ReceiverOptions;
import reactor.rabbitmq.RpcClient;
import reactor.rabbitmq.Sender;
import reactor.rabbitmq.SenderOptions;

import java.io.IOException;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * Allow getting a Mock of a Rabbit channel in Tests. Building also Sender and Receiver Options.
 */
public class WithRabbitEmitterMock implements BeforeEachCallback, ParameterResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(WithRabbitEmitterMock.class);
    private static final String P_RABBIT_CHANNEL = "rabbit-channel";
    private static final String P_RABBIT_SENDER_OPT = "rabbit-sender-opt";
    private static final String P_RABBIT_RECEIVER_OPT = "rabbit-receiver-opt";

    private static final String DEFAULT_RABBIT_REPLY_QUEUE_NAME = "amq.rabbitmq.reply-to";

    private static final Scheduler SCHEDULER = Schedulers.elastic();
    private static final int TIMEOUT_DURATION = 1;
    private ObjectMapper objectMapper;
    private String queueName;
    private String exchangeQueueName;
    private Object message;
    private Supplier<String> idGenerator;

    private WithRabbitEmitterMock() {
        objectMapper = new ObjectMapper();
    }

    public static WithRabbitMockBuilder builder() {
        return new WithRabbitMockBuilder();
    }

    @Override
    public void beforeEach(ExtensionContext context) throws IOException {
        Connection conn = new MockConnectionFactory().newConnection();
        Channel channel = conn.createChannel();

        if (queueName != null && exchangeQueueName != null) {
            declareAndBindQueues(channel);
        }

        SenderOptions senderOptions = declareSenderOptions(conn, channel);

        ReceiverOptions receiverOptions = declareReceiverOptions(conn);

        Store store = getStore(context);
        store.put(P_RABBIT_CHANNEL, channel);
        store.put(P_RABBIT_SENDER_OPT, senderOptions);
        store.put(P_RABBIT_RECEIVER_OPT, receiverOptions);
    }

    private ReceiverOptions declareReceiverOptions(Connection conn) {
        return new ReceiverOptions()
                .connectionMono(Mono.fromCallable(() -> conn))
                .connectionSubscriptionScheduler(SCHEDULER);
    }

    private SenderOptions declareSenderOptions(Connection conn, Channel channel) {
        return new SenderOptions()
                .connectionMono(Mono.fromCallable(() -> conn))
                .channelMono(Mono.fromCallable(() -> channel))
                .resourceManagementScheduler(SCHEDULER);
    }

    private void declareAndBindQueues(Channel channel) throws IOException {
        channel.queueDeclare(queueName, false, false, true, null);
        channel.exchangeDeclare(exchangeQueueName, BuiltinExchangeType.DIRECT, false, true, null);
        channel.queueBind(queueName, exchangeQueueName, "");

        channel.queueDeclare(DEFAULT_RABBIT_REPLY_QUEUE_NAME, false, false, true, null);
    }

    /**
     * Declare a message to send into rabbit communication and send it
     *
     * @param message       The rabbit message to send
     * @param senderOptions The options used to send message
     * @return the result of sending ReviewResultMessage
     */
    public Mono<Delivery> emitWithReply(Object message, SenderOptions senderOptions) {
        this.message = message;
        return Mono.using(() -> RabbitFlux.createSender(senderOptions),
                this::processEmission,
                Sender::close
        );
    }

    private Mono<Delivery> processEmission(Sender sender) {
        return Mono.just(message)
                .flatMap(message -> {
                    final RpcClient rpcClient = sender.rpcClient(
                            exchangeQueueName,
                            "",
                            idGenerator
                    );

                    Mono<Delivery> rpcMono = rpcClient.rpc(buildRpcRequest(message))
                            .timeout(Duration.ofSeconds(TIMEOUT_DURATION))
                            .doOnError(e ->
                                    LOGGER.error("ProcessEmission failure with RPC Client '{}'. {}: {}",
                                            rpcClient, e.getClass(), e.getLocalizedMessage()));
                    rpcClient.close();
                    return rpcMono;
                });
    }

    private Mono<RpcClient.RpcRequest> buildRpcRequest(Object message) {
        return Mono.fromCallable(() -> buildRpcRequestFromContent(objectMapper.writeValueAsBytes(message)));
    }

    private RpcClient.RpcRequest buildRpcRequestFromContent(byte[] content) {
        final AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                .replyTo(DEFAULT_RABBIT_REPLY_QUEUE_NAME)
                .build();
        return new RpcClient.RpcRequest(properties, content);
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Class<?> aClass = parameterContext.getParameter().getType();
        return aClass.equals(Channel.class)
                || aClass.equals(SenderOptions.class)
                || aClass.equals(ReceiverOptions.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Class<?> aClass = parameterContext.getParameter().getType();
        if (Channel.class.equals(aClass)) {
            return getStore(extensionContext).get(P_RABBIT_CHANNEL);
        }
        if (SenderOptions.class.equals(aClass)) {
            return getStore(extensionContext).get(P_RABBIT_SENDER_OPT);
        }
        if (ReceiverOptions.class.equals(aClass)) {
            return getStore(extensionContext).get(P_RABBIT_RECEIVER_OPT);
        }
        throw new ParameterResolutionException("Unable to resolve parameter for Rabbit Channel !");
    }

    public Channel getRabbitChannel(ExtensionContext context) {
        return getStore(context).get(P_RABBIT_CHANNEL, Channel.class);
    }

    private Store getStore(ExtensionContext context) {
        return context.getStore(ExtensionContext.Namespace.create(getClass()));
    }

    public SenderOptions getSenderOptions(ExtensionContext context) {
        return getStore(context).get(P_RABBIT_SENDER_OPT, SenderOptions.class);
    }

    public ReceiverOptions getReceiverOptions(ExtensionContext context) {
        return getStore(context).get(P_RABBIT_RECEIVER_OPT, ReceiverOptions.class);
    }

    /**
     * Allow to build a Channel rabbit
     * <p>
     * Usage :
     * <pre style="code">
     *     {@literal @}RegisterExtension
     *     WithRabbitMock wRabbitMock = WithRabbitMock.builder()
     *             .declareQueueAndExchange("queue-name", "exchange-queue-name")
     *             .declareReplyQueue("amq.rabbitmq.reply-to")
     *             .build();
     * </pre>
     */
    public static class WithRabbitMockBuilder {

        private String queueName;
        private String exchangeQueueName;
        private Supplier<String> idGenerator;

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
         * Declare a supplier to generate ids.
         *
         * @param idGenerator Supplier requested
         * @return the builder
         */
        public WithRabbitMockBuilder declareSupplier(Supplier<String> idGenerator) {
            this.idGenerator = idGenerator;
            return this;
        }

        /**
         * Build the Object Mapper junit extension
         *
         * @return The extension
         */
        public WithRabbitEmitterMock build() {
            WithRabbitEmitterMock withRabbitListenerMock = new WithRabbitEmitterMock();
            withRabbitListenerMock.queueName = this.queueName;
            withRabbitListenerMock.exchangeQueueName = this.exchangeQueueName;
            withRabbitListenerMock.idGenerator = this.idGenerator;

            return withRabbitListenerMock;
        }
    }
}
