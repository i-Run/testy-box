package fr.irun.testy.mongo;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import de.flapdoodle.embed.mongo.Command;
import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.IMongodConfig;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.config.RuntimeConfigBuilder;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.config.IRuntimeConfig;
import de.flapdoodle.embed.process.config.io.ProcessOutput;
import de.flapdoodle.embed.process.io.Processors;
import de.flapdoodle.embed.process.io.Slf4jLevel;
import de.flapdoodle.embed.process.runtime.Network;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;

import java.io.IOException;
import java.lang.reflect.Parameter;
import java.net.InetAddress;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Allow to launch en Embedded Mongo DB
 * <p>
 * The flapdoodle download the expected version of Mongo DB in <code>~/.embedmongo</code> and
 * run a database for the test.
 * </p><p>
 * From this database, an async {@link MongoClient} is created and a Spring {@link ReactiveMongoDatabaseFactory} wrap it.
 * </p>
 *
 * @see <a href="https://github.com/flapdoodle-oss/de.flapdoodle.embed.mongo">flapdoodle</a>
 */
public class WithEmbeddedMongo implements BeforeAllCallback, AfterAllCallback, ParameterResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(WithEmbeddedMongo.class);

    private static final Namespace NAMESPACE = Namespace.create(WithEmbeddedMongo.class);

    private static final String P_MONGOD = "mongod";
    private static final String P_MONGO_EXE = "mongoExe";
    private static final String P_MONGO_CLIENT = "mongoClient";
    private static final String P_MONGO_FACTORY = "reactiveMongoFactory";
    private static final String P_MONGO_DB_NAME = "mongoDbName";

    private final String databaseName;
    private final AtomicReference<ReactiveMongoDatabaseFactory> atomicMongoFactory;

    public WithEmbeddedMongo() {
        this(UUID.randomUUID().toString());
    }

    private WithEmbeddedMongo(String databaseName) {
        this.databaseName = databaseName;
        this.atomicMongoFactory = new AtomicReference<>();
    }

    /**
     * Retrieve the latest initialized Factory. Avoid to require {@link ExtensionContext} and allow SpringBoot mock.
     * <p>
     * If possible, prefer inject {@link ReactiveMongoDatabaseFactory} in test method instead.
     *
     * @return the {@link ReactiveMongoDatabaseFactory} created in the beforeAll.
     */
    public ReactiveMongoDatabaseFactory getMongoFactory() {
        return atomicMongoFactory.updateAndGet(old -> {
            assert old != null : "No Mongo factory initialized !";
            return old;
        });
    }

    public ReactiveMongoDatabaseFactory getMongoFactory(ExtensionContext context) {
        return getStore(context).get(P_MONGO_FACTORY, ReactiveMongoDatabaseFactory.class);
    }

    @Override
    public void beforeAll(ExtensionContext context) throws IOException {
        int freeServerPort = Network.getFreeServerPort(InetAddress.getLoopbackAddress());
        IMongodConfig mongoConfig = new MongodConfigBuilder()
                .net(new Net(InetAddress.getLoopbackAddress().getHostAddress(), freeServerPort, false))
                .version(Version.Main.PRODUCTION)
                .build();

        IRuntimeConfig runtimeConfig = new RuntimeConfigBuilder()
                .defaults(Command.MongoD)
                .processOutput(new ProcessOutput(
                        Processors.logTo(LOGGER, Slf4jLevel.INFO),
                        Processors.logTo(LOGGER, Slf4jLevel.ERROR),
                        Processors.logTo(LOGGER, Slf4jLevel.INFO)))
                .build();

        MongodStarter runtime = MongodStarter.getInstance(runtimeConfig);

        MongodExecutable mongodExe = runtime.prepare(mongoConfig);

        MongoClient mongo = MongoClients.create(String.format("mongodb://%s:%d/%s",
                mongoConfig.net().getServerAddress().getHostAddress(),
                mongoConfig.net().getPort(),
                databaseName));

        ReactiveMongoDatabaseFactory mongoFactory = new SimpleReactiveMongoDatabaseFactory(mongo, databaseName);
        if (!this.atomicMongoFactory.compareAndSet(null, mongoFactory)) {
            throw new IllegalStateException("Mongo factory already initialized ! Multiple Mongo factory not supported !");
        }

        MongodProcess mongod = mongodExe.start();

        Store store = getStore(context);
        store.put(P_MONGO_DB_NAME, databaseName);
        store.put(P_MONGO_EXE, mongodExe);
        store.put(P_MONGOD, mongod);
        store.put(P_MONGO_CLIENT, mongo);
        store.put(P_MONGO_FACTORY, mongoFactory);
    }

    @Override
    public void afterAll(ExtensionContext context) {
        Store store = getStore(context);
        MongoClient mongo = store.get(P_MONGO_CLIENT, MongoClient.class);
        if (mongo != null) {
            mongo.close();
        }

        MongodProcess mongod = store.get(P_MONGOD, MongodProcess.class);
        if (mongod != null) {
            mongod.stop();
        }

        MongodExecutable mongodExe = store.get(P_MONGO_EXE, MongodExecutable.class);
        if (mongodExe != null) {
            mongodExe.stop();
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Parameter parameter = parameterContext.getParameter();
        Class<?> type = parameter.getType();
        return type.equals(MongoClient.class) || type.equals(ReactiveMongoDatabaseFactory.class)
                || (type.equals(String.class) && parameter.isAnnotationPresent(MongoDatabaseName.class));
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Parameter parameter = parameterContext.getParameter();
        Class<?> type = parameter.getType();
        if (type.equals(MongoClient.class)) {
            return getStore(extensionContext).get(P_MONGO_CLIENT);
        } else if (type.equals(ReactiveMongoDatabaseFactory.class)) {
            return getStore(extensionContext).get(P_MONGO_FACTORY);
        } else if (type.equals(String.class) && parameter.isAnnotationPresent(MongoDatabaseName.class)) {
            return getStore(extensionContext).get(P_MONGO_DB_NAME);
        }

        throw new ParameterResolutionException(getClass().getName() + " must be static and package-protected !");
    }

    private Store getStore(ExtensionContext context) {
        return context.getStore(NAMESPACE);
    }

    public static WithEmbeddedMongoBuilder builder() {
        return new WithEmbeddedMongoBuilder();
    }

    public static final class WithEmbeddedMongoBuilder {
        private String databaseName = UUID.randomUUID().toString();

        public WithEmbeddedMongoBuilder setDatabaseName(String databaseName) {
            this.databaseName = databaseName;
            return this;
        }

        public WithEmbeddedMongo build() {
            return new WithEmbeddedMongo(databaseName);
        }
    }
}
