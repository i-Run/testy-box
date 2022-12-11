package fr.ght1pc9kc.testy.beat.brokers;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

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

    /**
     * Obtain the connection factory.
     *
     * @return Connection factory for the broker.
     */
    ConnectionFactory getConnectionFactory();
}
