package fr.irun.test.mongo;

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
import org.junit.jupiter.api.extension.*;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;

import java.lang.reflect.Parameter;
import java.net.InetAddress;
import java.util.UUID;

/**
 * Allow to launch en Embedded Mongo DB
 * <p>
 * The flapdoodle download the expected version of Mongo DB in <code>~/.embedmongo</code> and
 * run a database for the test.
 * <p>
 * From this database, an async {@link MongoClient} is created and a Spring {@link ReactiveMongoDatabaseFactory} wrap it.
 *
 * @see <a href="https://github.com/flapdoodle-oss/de.flapdoodle.embed.mongo">flapdoodle</a>
 */
public class WithEmbeddedMongo implements BeforeEachCallback, AfterEachCallback, ParameterResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(WithEmbeddedMongo.class);


    private static final String MONGOD = "mongod";
    private static final String MONGO_EXE = "mongoExe";
    private static final String MONGO_CLIENT = "mongoClient";
    private static final String MONGO_FACTORY = "reactiveMongoFactory";
    private static final String MONGO_DB_NAME = "mongoDbName";

    private final String databaseName;

    public WithEmbeddedMongo() {
        this.databaseName = UUID.randomUUID().toString();
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
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
        MongodProcess mongod = mongodExe.start();

        MongoClient mongo = MongoClients.create(String.format("mongodb://%s:%d/%s",
                mongoConfig.net().getServerAddress().getHostAddress(),
                mongoConfig.net().getPort(),
                databaseName));

        ReactiveMongoDatabaseFactory mongoFactory = new SimpleReactiveMongoDatabaseFactory(mongo, databaseName);

        Store store = getStore(context);
        store.put(MONGO_DB_NAME, databaseName);
        store.put(MONGO_EXE, mongodExe);
        store.put(MONGOD, mongod);
        store.put(MONGO_CLIENT, mongo);
        store.put(MONGO_FACTORY, mongoFactory);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        Store store = getStore(context);
        MongoClient mongo = store.get(MONGO_CLIENT, MongoClient.class);
        if (mongo != null) {
            mongo.close();
        }

        MongodProcess mongod = store.get(MONGOD, MongodProcess.class);
        if (mongod != null) {
            mongod.stop();
        }

        MongodExecutable mongodExe = store.get(MONGO_EXE, MongodExecutable.class);
        if (mongodExe != null)
            mongodExe.stop();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Parameter parameter = parameterContext.getParameter();
        Class<?> type = parameter.getType();
        return type.equals(MongoClient.class) || type.equals(ReactiveMongoDatabaseFactory.class) ||
                (type.equals(String.class) && parameter.isAnnotationPresent(MongoDatabaseName.class));
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Parameter parameter = parameterContext.getParameter();
        Class<?> type = parameter.getType();
        if (type.equals(MongoClient.class)) {
            return getStore(extensionContext).get(MONGO_CLIENT);
        } else if (type.equals(ReactiveMongoDatabaseFactory.class)) {
            return getStore(extensionContext).get(MONGO_FACTORY);
        } else if (type.equals(String.class) && parameter.isAnnotationPresent(MongoDatabaseName.class)) {
            return getStore(extensionContext).get(MONGO_DB_NAME);
        }

        return null;
    }

    private Store getStore(ExtensionContext context) {
        return context.getStore(Namespace.create(getClass(), context.getRequiredTestMethod()));
    }
}
