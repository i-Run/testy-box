package fr.irun.testy.beat.brokers;

import com.rabbitmq.client.Connection;

/**
 * Methods to be implemented by an embedded broker.
 */
public interface EmbeddedBroker {

    /**
     * Start the broker.
     */
    void start();

    /**
     * Build a new connection to the broker.
     *
     * @return The built connection.
     */
    Connection newConnection();

    /**
     * Stop the broker.
     */
    void stop();
}
