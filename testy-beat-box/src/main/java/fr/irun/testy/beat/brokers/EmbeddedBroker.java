package fr.irun.testy.beat.brokers;

import com.google.common.collect.ImmutableMap;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.apache.qpid.server.SystemLauncher;

import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Class wrapping an embedded AMQP broker.
 */
public final class EmbeddedBroker {
    private static final String CONFIG_FILE = "embedded-broker.json";
    private static final String CONNECTION_USERNAME = "obiwan";
    private static final String CONNECTION_PASS = "kenobi";
    private static final String HOST = "localhost";
    private static final int PORT = 9595;

    private final SystemLauncher systemLauncher;
    private final ConnectionFactory connectionFactory;

    /**
     * Default constructor for the broker.
     */
    public EmbeddedBroker() {
        this.systemLauncher = new SystemLauncher();
        this.connectionFactory = new ConnectionFactory();
        connectionFactory.setUsername(CONNECTION_USERNAME);
        connectionFactory.setPassword(CONNECTION_PASS);
        connectionFactory.setHost(HOST);
        connectionFactory.setPort(PORT);
    }

    /**
     * Start the embedded AMQP broker.
     */
    public void start() {
        try {
            this.systemLauncher.startup(createConfiguration());
        } catch (Exception e) {
            throw new IllegalStateException("Error when starting embedded broker", e);
        }
    }

    private Map<String, Object> createConfiguration() {
        URL initialConfig = getClass().getClassLoader().getResource(CONFIG_FILE);
        if (initialConfig == null) {
            throw new IllegalStateException("Not found config file " + CONFIG_FILE);
        }

        return ImmutableMap.<String, Object>builder()
                .put("type", "Memory")
                .put("initialConfigurationLocation", initialConfig.toExternalForm())
                .put("startupLoggedToSystemOut", true)
                .build();
    }

    /**
     * Open a new connection on the broker.
     *
     * @return The opened connection.
     */
    public Connection newConnection() {
        try {
            return connectionFactory.newConnection();
        } catch (IOException | TimeoutException e) {
            throw new IllegalStateException("Error when opening connection to embedded broker", e);
        }
    }

    /**
     * Stop the AMQP broker.
     */
    public void stop() {
        this.systemLauncher.shutdown();
    }
}
