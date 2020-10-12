package fr.irun.testy.beat.messaging;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Delivery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.io.IOException;

/**
 * Consumer linked to {@link MockedReceiverFactory}.
 */
public final class MockedReceiver {

    private static final Logger LOGGER = LoggerFactory.getLogger(MockedReceiver.class);

    private final Channel channel;
    private final String queue;
    private final MockedConsumer consumer;

    /**
     * Constructor.
     *
     * @param channel  Channel where the messages are received.
     * @param queue    Queue on which the messages are consumed.
     * @param consumer Consumer used to consume messages.
     */
    MockedReceiver(Channel channel,
                   String queue,
                   MockedConsumer consumer) {
        this.channel = channel;
        this.queue = queue;
        this.consumer = consumer;
    }

    void start() {
        try {
            LOGGER.debug("Start consuming on queue '{}' with consumer '{}'", queue, consumer.getConsumerTag());
            channel.basicConsume(queue, true, consumer);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Obtain all the messages received by this receiver.
     *
     * @return Flux of messages received by this receiver.
     */
    public Flux<Delivery> getReceivedMessages() {
        return consumer.getRequests();
    }

}
