package fr.ght1pc9kc.testy.redis;

import fr.ght1pc9kc.testy.core.utils.PortUtils;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import redis.embedded.RedisServer;

import java.lang.reflect.Parameter;
import java.time.Duration;

/**
 * Allow to launch en Embedded Redis DB
 * <p>
 * Use <strong>embedded-redis</strong> from <strong>codemonstur</strong> which seem to be the most maintained fork
 * of the original <em>kstyrc</em> version
 * </p><p>
 * From this database, an async {@link RedisServer} is created and shared in the context.
 * </p>
 * <p>This extension allow to injection of :
 *  <ul>
 *      <li><code>{@literal @}RedisPort int redisPort</code>: The redis listening port</li>
 *      <li><code>RedisClient client</code>: The redis lettuce client connected to the embedded server</li>
 *      <li><code>RedisServer server</code>: The redis server itself</li>
 *  </ul>
 * </p>
 * <h2>Usage :</h2>
 * <h3>With <code>{@literal @}ExtendWith</code></h3>
 * <pre><code>
 * {@literal @}ExtendWith(WithEmbeddedRedis.class)
 *  class WithEmbeddedRedisTest {
 *     {@literal @}Test
 *      void should_use_embedded_redis(RedisClient client) {
 *         try (StatefulRedisConnection<String, String> conn = client.connect()) {
 *             RedisCommands<String, String> redisCommands = conn.sync();
 *             String actual = redisCommands.set("key", "Hello, Redis!");
 *         }
 *     }
 *  }
 * </code></pre>
 *
 * <h3>With <code>{@literal @}RegisterExtension</code></h3>
 * <pre><code>
 * {@literal @}RegisterExtension
 *  public static WithEmbeddedRedis mockRedis = WithEmbeddedRedis.builder().build();
 *
 * {@literal @}Test
 *  void should_use_embedded_redis(RedisClient client) {
 *      try (StatefulRedisConnection<String, String> conn = client.connect()) {
 *          RedisCommands<String, String> redisCommands = conn.sync();
 *          String actual = redisCommands.set("key", "Hello, Redis!");
 *      }
 *  }
 * </code></pre>
 *
 * @see <a href="https://github.com/codemonstur/embedded-redis">codemonstur/embedded-redis</a>
 * @see <a href="https://lettuce.io/">lettuce.io</a>
 */
@Slf4j
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithEmbeddedRedis implements BeforeAllCallback, AfterAllCallback, ParameterResolver {
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(WithEmbeddedRedis.class);

    private static final String P_REDIS_PORT = "redisPort";
    private static final String P_REDIS_CLIENT = "redisClient";
    private static final String P_REDIS_SERVER = "redisServer";

    @Builder.Default
    private final int redisPort = PortUtils.randomFreePort();

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        RedisServer server = new RedisServer(redisPort);

        RedisURI clientUri = RedisURI.create("localhost", server.ports().get(0));
        RedisClient client = RedisClient.create(clientUri);
        log.atDebug().addArgument(clientUri).setMessage("Embedded Redis server started at {}").log();

        ExtensionContext.Store store = getStore(context);
        store.put(P_REDIS_PORT, server.ports().get(0));
        store.put(P_REDIS_CLIENT, client);
        store.put(P_REDIS_SERVER, server);

        server.start();
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        ExtensionContext.Store store = getStore(context);

        RedisClient client = store.get(P_REDIS_CLIENT, RedisClient.class);
        client.shutdown(Duration.ofSeconds(2), Duration.ofSeconds(2));

        RedisServer server = store.get(P_REDIS_SERVER, RedisServer.class);
        server.stop();
        log.atDebug().log("Embedded Redis server stopped");

    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        Parameter parameter = parameterContext.getParameter();
        Class<?> type = parameter.getType();
        return RedisClient.class.equals(type)
                || RedisServer.class.equals(type)
                || (Integer.class.equals(type) && parameter.isAnnotationPresent(RedisPort.class));
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        Parameter parameter = parameterContext.getParameter();
        Class<?> type = parameter.getType();
        if (RedisClient.class.equals(type)) {
            return getStore(extensionContext).get(P_REDIS_CLIENT);
        } else if (RedisServer.class.equals(type)) {
            return getStore(extensionContext).get(P_REDIS_SERVER);
        } else if (type.equals(Integer.class) && parameter.isAnnotationPresent(RedisPort.class)) {
            return getStore(extensionContext).get(P_REDIS_PORT);
        }

        throw new ParameterResolutionException(getClass().getName() + " must be static and package-protected !");
    }

    private ExtensionContext.Store getStore(ExtensionContext context) {
        return context.getStore(NAMESPACE);
    }
}
