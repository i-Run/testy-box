package fr.ght1pc9kc.testy.beat.messaging;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Delivery;
import com.rabbitmq.client.Envelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.io.IOException;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Internal consumer to consume messages on a queue and respond.
 */
final class MockedConsumer extends DefaultConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(MockedConsumer.class);

    private final Queue<AmqpMessage> responses;
    private final AtomicInteger remainingRequests;
    private final AtomicReference<AmqpMessage> currentResponse = new AtomicReference<>();

    private final Sinks.Many<Delivery> requests = Sinks.many().multicast().onBackpressureBuffer();

    /**
     * Constructor.
     *
     * @param channel    Channel used to consume on queue.
     * @param nbRequests Number of requests to consume. When reached, this consumer is canceled.
     * @param responses  All the responses to reply in order. The last response is indefinitely replied if there are less responses than requests.
     */
    MockedConsumer(Channel channel,
                   int nbRequests,
                   Queue<AmqpMessage> responses) {
        super(channel);
        this.responses = responses;
        this.remainingRequests = new AtomicInteger(nbRequests);
    }

    /**
     * Obtain all the requests received by the consumer.
     *
     * @return All the requests received by the consumer.
     */
    Flux<Delivery> getReceivedRequests() {
        return requests.asFlux();
    }

    @Override
    public void handleDelivery(String consumerTag,
                               Envelope envelope,
                               AMQP.BasicProperties properties,
                               byte[] body) throws IOException {

        try {
            LOGGER.debug("Consume message '{}'", properties.getMessageId());

            final int remaining = remainingRequests.decrementAndGet();
            if (remaining >= 0) {
                requests.tryEmitNext(new Delivery(envelope, properties, body));
                if (canReply(properties)) {
                    replyToMessage(properties);
                }
            }
            if (remaining <= 0) {
                LOGGER.debug("Stop consumer with tag '{}'", consumerTag);
                getChannel().basicCancel(consumerTag);
                requests.tryEmitComplete();
            }
        } catch (IOException ex) {
            requests.tryEmitError(ex);
            throw ex;
        }
    }

    private boolean canReply(AMQP.BasicProperties requestProperties) {
        return requestProperties.getReplyTo() != null && requestProperties.getCorrelationId() != null;
    }

    private void replyToMessage(AMQP.BasicProperties requestProperties) throws IOException {
        final AmqpMessage response = Optional.ofNullable(responses.poll())
                .map(d -> {
                    currentResponse.set(d);
                    return d;
                }).orElseGet(currentResponse::get);

        final AMQP.BasicProperties responseProperties = new AMQP.BasicProperties.Builder()
                .headers(response.headers)
                .correlationId(requestProperties.getCorrelationId())
                .build();

        LOGGER.debug("Reply to message '{}'", requestProperties.getMessageId());
        getChannel().basicPublish(
                "",
                requestProperties.getReplyTo(),
                responseProperties,
                response.body
        );
    }
}
