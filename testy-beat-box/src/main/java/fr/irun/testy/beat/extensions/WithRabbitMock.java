package fr.irun.testy.beat.extensions;

import com.github.fridujo.rabbitmq.mock.MockConnectionFactory;
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
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.rabbitmq.ReceiverOptions;
import reactor.rabbitmq.SenderOptions;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

import static fr.irun.testy.beat.messaging.AMQPHelper.declareAndBindQueues;
import static fr.irun.testy.beat.messaging.AMQPHelper.declareConsumer;
import static fr.irun.testy.beat.messaging.AMQPHelper.declareReceiverOptions;
import static fr.irun.testy.beat.messaging.AMQPHelper.declareSenderOptions;

/**
 * Allow getting a Mock of a Rabbit channel in Tests. Building also Sender and Receiver Options.
 */
public final class WithRabbitMock implements BeforeEachCallback, ParameterResolver {
    private static final int QUEUE_CAPACITY = 10;
    private static final Logger LOGGER = LoggerFactory.getLogger(WithRabbitMock.class);
    private static final String P_RABBIT_CHANNEL = "rabbit-channel";
    private static final String P_RABBIT_SENDER_OPT = "rabbit-sender-opt";
    private static final String P_RABBIT_RECEIVER_OPT = "rabbit-receiver-opt";

    private static final Scheduler SCHEDULER = Schedulers.elastic();
    private final String queueName;
    private final String exchangeQueueName;
    private final Object replyMessage;

    private WithRabbitMock(String queueName, String exchangeQueueName, Object replyMessage) {
        this.queueName = queueName;
        this.exchangeQueueName = exchangeQueueName;
        this.replyMessage = replyMessage;
    }

    public static WithRabbitMockBuilder builder() {
        return new WithRabbitMockBuilder();
    }

    @Override
    public void beforeEach(ExtensionContext context) throws IOException {
        Connection conn = new MockConnectionFactory().newConnection();
        Channel channel = conn.createChannel();

        if (queueName != null && exchangeQueueName != null) {
            declareAndBindQueues(channel, queueName, exchangeQueueName);
        }

        SenderOptions senderOptions = declareSenderOptions(conn, channel, SCHEDULER);

        ReceiverOptions receiverOptions = declareReceiverOptions(conn, SCHEDULER);

        Store store = getStore(context);
        store.put(P_RABBIT_CHANNEL, channel);
        store.put(P_RABBIT_SENDER_OPT, senderOptions);
        store.put(P_RABBIT_RECEIVER_OPT, receiverOptions);
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Class<?> aClass = parameterContext.getParameter().getType();
        return aClass.equals(Channel.class)
                || aClass.equals(SenderOptions.class)
                || aClass.equals(ReceiverOptions.class)
                || aClass.equals(Queue.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Class<?> aClass = parameterContext.getParameter().getType();
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
            Queue<Delivery> messages = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
            if (queueName != null && exchangeQueueName != null) {
                Channel channel = (Channel) getStore(extensionContext).get(P_RABBIT_CHANNEL);
                declareConsumer(channel, messages, queueName, replyMessage);
            }
            return messages;
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
     *             .declareReplyMessage("Reception OK. Reply Message !")
     *             .build();
     * </pre>
     */
    public static class WithRabbitMockBuilder {

        private String queueName;
        private String exchangeQueueName;
        private Object replyMessage;

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
            this.replyMessage = replyMessage;
            return this;
        }

        /**
         * Build the Rabbit Mock junit extension
         *
         * @return The extension
         */
        public WithRabbitMock build() {
            return new WithRabbitMock(queueName, exchangeQueueName, replyMessage);
        }
    }
}
