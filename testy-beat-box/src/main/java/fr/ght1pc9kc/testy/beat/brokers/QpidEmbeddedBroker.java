package fr.ght1pc9kc.testy.beat.brokers;

import com.google.common.collect.ImmutableMap;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.apache.qpid.server.SystemLauncher;
import org.apache.qpid.server.model.SystemConfig;

import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Class wrapping an embedded AMQP broker provided by Apache QPID.
 */
public final class QpidEmbeddedBroker implements EmbeddedBroker {

    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_PORT = 9595;
    public static final String DEFAULT_USERNAME = "obiwan";
    public static final String DEFAULT_PASS = "kenobi";

    private static final String DEFAULT_CONFIG_FILE = "embedded-broker.json";
    private final SystemLauncher systemLauncher;
    private final ConnectionFactory connectionFactory;

    private final String configurationFile;

    /**
     * Default constructor for the broker.
     * By default the values are:
     * <ul>
     *     <li>Configuration file: embedded-broker.json (provided by Testy-beat-box)</li>
     *     <li>Host: localhost</li>
     *     <li>Port: 9595</li>
     *     <li>Username: obiwan</li>
     * </ul>
     */
    public QpidEmbeddedBroker() {
        this(DEFAULT_CONFIG_FILE, DEFAULT_HOST, DEFAULT_PORT, DEFAULT_USERNAME, DEFAULT_PASS);
    }

    /**
     * Create a customized broker.
     *
     * @param configurationFile The path to the configuration file of the broker.
     * @param host              The host running the broker.
     * @param port              The port to access to a connection.
     * @param username          Username to get a connection.
     * @param password          Password to get a connection.
     */
    public QpidEmbeddedBroker(String configurationFile,
                              String host,
                              int port,
                              String username,
                              String password) {
        this.configurationFile = configurationFile;
        this.systemLauncher = new SystemLauncher();
        this.connectionFactory = new ConnectionFactory();
        connectionFactory.setHost(host);
        connectionFactory.setPort(port);
        connectionFactory.setUsername(username);
        connectionFactory.setPassword(password);
    }

    /**
     * Start the embedded AMQP broker.
     */
    @Override
    public void start() {
        try {
            this.systemLauncher.startup(createConfiguration());
        } catch (Exception e) {
            throw new IllegalStateException("Error when starting embedded broker", e);
        }
    }

    private Map<String, Object> createConfiguration() {
        URL initialConfig = getClass().getClassLoader().getResource(configurationFile);
        if (initialConfig == null) {
            throw new IllegalStateException("Not found config file " + configurationFile);
        }

        return ImmutableMap.<String, Object>builder()
                .put("type", "Memory")
                .put(SystemConfig.INITIAL_CONFIGURATION_LOCATION, initialConfig.toExternalForm())
                .put(SystemConfig.STARTUP_LOGGED_TO_SYSTEM_OUT, false)
                .build();
    }

    /**
     * Open a new connection on the broker.
     *
     * @return The opened connection.
     */
    @Override
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
    @Override
    public void stop() {
        this.systemLauncher.shutdown();
    }

    @Override
    public ConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }
}
