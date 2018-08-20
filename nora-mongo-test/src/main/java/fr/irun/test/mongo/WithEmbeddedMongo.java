package fr.irun.test.mongo;

import com.mongodb.async.client.MongoClient;
import com.mongodb.async.client.MongoClients;
import com.mongodb.reactivestreams.client.internal.MongoClientImpl;
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
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;

import java.net.InetAddress;
import java.util.UUID;

/**
 * Allow to launch en Embedded Mongo DB
 * <p>
 * The flapdoodle download the expected version of Mongo DB in <code>~/.embedmongo</code> and
 * run a database for the test.
 * <p>
 * From this database, an async {@link MongoClient} is created and a Spring {@link ReactiveMongoTemplate} wrap it.
 *
 * @see <a href="https://github.com/flapdoodle-oss/de.flapdoodle.embed.mongo">flapdoodle</a>
 */
public class WithEmbeddedMongo implements BeforeEachCallback, AfterEachCallback, ParameterResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(WithEmbeddedMongo.class);

    private static final String MONGOD = "mongod";
    private static final String MONGO_EXE = "mongoExe";
    private static final String MONGO_CLIENT = "mongoClient";
    private static final String REACTIVE_MONGO_TEMPLATE = "reactiveMongoTemplate";
    private static final String MONGO_DB_NAME = "mongoDbName";

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
    public void beforeEach(ExtensionContext context) throws Exception {
        String databaseName = UUID.randomUUID().toString();

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

        ReactiveMongoTemplate mongoTemplate = new ReactiveMongoTemplate(new MongoClientImpl(mongo), databaseName);

        Store store = getStore(context);
        store.put(MONGO_DB_NAME, databaseName);
        store.put(MONGO_EXE, mongodExe);
        store.put(MONGOD, mongod);
        store.put(MONGO_CLIENT, mongo);
        store.put(REACTIVE_MONGO_TEMPLATE, mongoTemplate);
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        Class<?> type = parameterContext.getParameter().getType();
        return type.equals(MongoClient.class) || type.equals(ReactiveMongoTemplate.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        Class<?> type = parameterContext.getParameter().getType();
        if (type.equals(MongoClient.class)) {
            return getStore(extensionContext).get(MONGO_CLIENT);
        } else if (type.equals(ReactiveMongoTemplate.class)) {
            return getStore(extensionContext).get(REACTIVE_MONGO_TEMPLATE);
        }

        return null;
    }

    private Store getStore(ExtensionContext context) {
        return context.getStore(Namespace.create(getClass(), context.getRequiredTestMethod()));
    }
}
