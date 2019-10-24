package fr.irun.testy.beat.extensions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fridujo.rabbitmq.mock.MockConnectionFactory;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import fr.irun.review.api.model.ReviewResultMessage;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.rabbitmq.ReceiverOptions;
import reactor.rabbitmq.SenderOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Allow getting a Mock of a Rabbit channel in Tests. Building also Sender and Receiver Options.
 */
public class WithRabbitListenerMock implements BeforeAllCallback, BeforeEachCallback, ParameterResolver {
    private static final String P_RABBIT_CHANNEL = "rabbit-channel";
    private static final String P_RABBIT_SENDER_OPT = "rabbit-sender-opt";
    private static final String P_RABBIT_RECEIVER_OPT = "rabbit-receiver-opt";
    private static final String P_RABBIT_MESSAGE_RECEIVED = "rabbit-msg-received";

    private static final String DEFAULT_RABBIT_REPLY_QUEUE_NAME = "amq.rabbitmq.reply-to";

    private static final Scheduler SCHEDULER = Schedulers.elastic();
    private String queueName;
    private String exchangeQueueName;
    private String replyQueueName;

    public WithRabbitListenerMock() {
    }

    public static WithRabbitMockBuilder builder() {
        return new WithRabbitMockBuilder();
    }

    @Override
    public void beforeAll(ExtensionContext context) throws IOException {
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

        if (queueName != null && exchangeQueueName != null) {
            List<String> messages = declareConsumer(channel);
            store.put(P_RABBIT_MESSAGE_RECEIVED, messages);
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) throws IOException {
        beforeAll(context);
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

        channel.queueDeclare(replyQueueName, false, false, true, null);
    }

    private List<String> declareConsumer(Channel channel) throws IOException {
        List<String> messages = new ArrayList<>();
        channel.basicConsume(queueName, true,
                new DefaultConsumer(channel) {
                    @Override
                    public void handleDelivery(String consumerTag,
                                               Envelope envelope,
                                               AMQP.BasicProperties properties,
                                               byte[] body) throws IOException {
                        messages.add(new String(body));
                        ObjectMapper objectMapper = new ObjectMapper();
                        channel.basicPublish(
                                "",
                                properties.getReplyTo(),
                                new AMQP.BasicProperties.Builder().correlationId(properties.getCorrelationId()).build(),
                                objectMapper.writeValueAsBytes(ReviewResultMessage.builder().jobSuccess(true).build()));
                    }
                });
        return messages;
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Class<?> aClass = parameterContext.getParameter().getType();
        return aClass.equals(Channel.class)
                || aClass.equals(SenderOptions.class)
                || aClass.equals(ReceiverOptions.class)
                || aClass.equals(List.class);
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
        if (List.class.equals(aClass)) {
            return getStore(extensionContext).get(P_RABBIT_MESSAGE_RECEIVED);
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

    public List getMessagesReceived(ExtensionContext context) {
        return getStore(context).get(P_RABBIT_MESSAGE_RECEIVED, List.class);
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
        private String replyQueueName;

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
         * Declare the queue for reply for rabbit communication
         *
         * @param replyQueueName The name of queue for reply
         * @return the builder
         */
        public WithRabbitMockBuilder declareReplyQueue(String replyQueueName) {
            this.replyQueueName = replyQueueName;
            return this;
        }

        /**
         * Build the Object Mapper junit extension
         *
         * @return The extension
         */
        public WithRabbitListenerMock build() {
            WithRabbitListenerMock withRabbitListenerMock = new WithRabbitListenerMock();
            withRabbitListenerMock.queueName = this.queueName;
            withRabbitListenerMock.exchangeQueueName = this.exchangeQueueName;

            if (replyQueueName == null || replyQueueName.isEmpty()) {
                withRabbitListenerMock.replyQueueName = DEFAULT_RABBIT_REPLY_QUEUE_NAME;
            } else {
                withRabbitListenerMock.replyQueueName = this.replyQueueName;
            }
            return withRabbitListenerMock;
        }
    }
}
