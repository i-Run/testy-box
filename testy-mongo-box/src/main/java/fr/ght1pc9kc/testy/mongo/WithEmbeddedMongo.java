package fr.ght1pc9kc.testy.mongo;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.transitions.Mongod;
import de.flapdoodle.embed.mongo.transitions.RunningMongodProcess;
import de.flapdoodle.embed.process.io.ProcessOutput;
import de.flapdoodle.reverse.transitions.Start;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;

import java.io.IOException;
import java.lang.reflect.Parameter;
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
@Slf4j
public class WithEmbeddedMongo implements BeforeAllCallback, AfterAllCallback, ParameterResolver {
    private static final Namespace NAMESPACE = Namespace.create(WithEmbeddedMongo.class);

    private static final String P_MONGO_PROCESS = "mongoPocess";
    private static final String P_MONGO_CLIENT = "mongoClient";
    private static final String P_MONGO_FACTORY = "reactiveMongoFactory";
    private static final String P_MONGO_TEMPLATE = "reactiveMongoTemplate";
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

    public ReactiveMongoTemplate getMongoTemplate(ExtensionContext context) {
        return getStore(context).get(P_MONGO_TEMPLATE, ReactiveMongoTemplate.class);
    }

    @Override
    public void beforeAll(ExtensionContext context) throws IOException {

        RunningMongodProcess process = Mongod.instance()
                .withProcessOutput(Start.to(ProcessOutput.class)
                        .initializedWith(ProcessOutput.named("Slf4j Logger", log)))
                .start(Version.Main.V6_0).current();

        MongoClient mongo = MongoClients.create(String.format("mongodb://%s:%d/%s",
                process.getServerAddress().getHost(),
                process.getServerAddress().getPort(),
                databaseName));

        ReactiveMongoDatabaseFactory mongoFactory = new SimpleReactiveMongoDatabaseFactory(mongo, databaseName);
        if (!this.atomicMongoFactory.compareAndSet(null, mongoFactory)) {
            throw new IllegalStateException("Mongo factory already initialized ! Multiple Mongo factory not supported !");
        }
        ReactiveMongoTemplate mongoTemplate = new ReactiveMongoTemplate(mongoFactory);

        Store store = getStore(context);
        store.put(P_MONGO_DB_NAME, databaseName);
        store.put(P_MONGO_PROCESS, process);
        store.put(P_MONGO_CLIENT, mongo);
        store.put(P_MONGO_FACTORY, mongoFactory);
        store.put(P_MONGO_TEMPLATE, mongoTemplate);
    }

    @Override
    public void afterAll(ExtensionContext context) {
        Store store = getStore(context);
        MongoClient mongo = store.get(P_MONGO_CLIENT, MongoClient.class);
        if (mongo != null) {
            mongo.close();
        }

        RunningMongodProcess running = store.get(P_MONGO_PROCESS, RunningMongodProcess.class);
        if (running != null) {
            running.stop();
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Parameter parameter = parameterContext.getParameter();
        Class<?> type = parameter.getType();
        return MongoClient.class.equals(type)
                || ReactiveMongoDatabaseFactory.class.equals(type)
                || ReactiveMongoTemplate.class.equals(type)
                || (String.class.equals(type) && parameter.isAnnotationPresent(MongoDatabaseName.class));
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Parameter parameter = parameterContext.getParameter();
        Class<?> type = parameter.getType();
        if (MongoClient.class.equals(type)) {
            return getStore(extensionContext).get(P_MONGO_CLIENT);
        } else if (ReactiveMongoDatabaseFactory.class.equals(type)) {
            return getStore(extensionContext).get(P_MONGO_FACTORY);
        } else if (ReactiveMongoTemplate.class.equals(type)) {
            return getStore(extensionContext).get(P_MONGO_TEMPLATE);
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
