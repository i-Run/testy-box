package fr.irun.testy.beat.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Delivery;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.stream.Stream;

/**
 * AMQP Receiver used for the unit tests.
 * It allows to:
 * <ul>
 *     <li>Consume the content of a queue and reply a customized message</li>
 *     <li>Provide the messages received from the consumed queue</li>
 * </ul>
 * An {@link AMQPReceiver} is automatically created for each queue added into the test extension.
 * This receiver is re-created for each test and can be injected as parameter.
 *
 * @deprecated Use instead {@link MockedReceiverFactory}, also injectable by Rabbit extension.
 */
@Deprecated
public final class AMQPReceiver {

    private static final int DEFAULT_QUEUE_CAPACITY = 10;

    /**
     * Name of the consumed queue.
     */
    public final String queueName;

    private final Channel amqpChannel;
    private final ObjectMapper objectMapper;
    private final Queue<Delivery> receivedMessages;

    private AMQPReceiver(String queueName,
                         Channel amqpChannel,
                         ObjectMapper objectMapper) {
        this.queueName = queueName;
        this.amqpChannel = amqpChannel;
        this.objectMapper = objectMapper;
        this.receivedMessages = new ArrayBlockingQueue<>(DEFAULT_QUEUE_CAPACITY);
    }

    /**
     * Consume on the input queue and reply a specific message.
     *
     * @param replyMessage The message to respond.
     */
    public void consumeAndReply(Object replyMessage) {
        AMQPHelper.declareConsumer(amqpChannel, objectMapper, receivedMessages, queueName, replyMessage);
    }

    /**
     * Obtain the next message delivered to this receiver.
     *
     * @return The next message delivered to this receiver.
     */
    public Optional<Delivery> getNextMessage() {
        return Optional.ofNullable(receivedMessages.poll());
    }

    /**
     * Obtain all the messages delivered to this receiver.
     *
     * @return All the messages delivered to this receiver.
     */
    public Stream<Delivery> getMessages() {
        return receivedMessages.stream();
    }

    /**
     * Create a new builder.
     *
     * @param queueName Name of the queue to receive.
     * @return {@link AMQPReceiverBuilder}.
     */
    public static AMQPReceiverBuilder builder(@Nonnull String queueName) {
        return new AMQPReceiverBuilder(queueName);
    }

    /**
     * Builder class for {@link AMQPReceiver}.
     */
    public static final class AMQPReceiverBuilder {

        private final String queueName;
        @Nullable
        private ObjectMapper objectMapper;

        private AMQPReceiverBuilder(String queueName) {
            this.queueName = queueName;
        }

        /**
         * Define a customized object mapper to convert the messages to objects.
         *
         * @param objectMapper Object mapper.
         * @return Builder instance.
         */
        public AMQPReceiverBuilder objectMapper(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
            return this;
        }

        /**
         * Build a new {@link AMQPReceiver} with the given Channel.
         *
         * @param amqpChannel AMQP channel to subscribe to the queue.
         * @return {@link AMQPReceiver} instance.
         */
        public AMQPReceiver build(@Nonnull Channel amqpChannel) {
            final ObjectMapper mapper = Optional.ofNullable(this.objectMapper)
                    .orElseGet(ObjectMapper::new);

            return new AMQPReceiver(queueName, amqpChannel, mapper);
        }
    }

}
