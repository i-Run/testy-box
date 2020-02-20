package fr.irun.testy.beat.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Delivery;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

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

    public void consume(Object replyMessage) {
        AMQPHelper.declareConsumer(amqpChannel, objectMapper, receivedMessages, queueName, replyMessage);
    }

    public Optional<Delivery> pollMessage() {
        return Optional.ofNullable(receivedMessages.poll());
    }

    public static AMQPReceiverBuilder builder(@Nonnull String queueName) {
        return new AMQPReceiverBuilder(queueName);
    }

    public static final class AMQPReceiverBuilder {
        private final String queueName;

        private ObjectMapper objectMapper;

        private AMQPReceiverBuilder(String queueName) {
            this.queueName = queueName;
        }

        public AMQPReceiverBuilder objectMapper(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
            return this;
        }

        public AMQPReceiver build(@Nonnull Channel amqpChannel) {
            final ObjectMapper mapper = Optional.ofNullable(this.objectMapper)
                    .orElseGet(ObjectMapper::new);

            return new AMQPReceiver(queueName, amqpChannel, mapper);
        }
    }

}
