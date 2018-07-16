package com.irun.nora.test.mongo;

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
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;

import java.net.InetAddress;
import java.util.Objects;

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
public class WithEmbeddedMongo extends ExternalResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(WithEmbeddedMongo.class);

    private final String databaseName;

    private MongodExecutable mongodExe;
    private MongodProcess mongod;
    private MongoClient mongo;
    private ReactiveMongoTemplate mongoTemplate;

    public WithEmbeddedMongo(String databaseName) {
        this.databaseName = Objects.requireNonNull(databaseName);
    }

    @Override
    protected void before() throws Throwable {
        super.before();

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

        mongodExe = runtime.prepare(mongoConfig);
        mongod = mongodExe.start();

        mongo = MongoClients.create(String.format("mongodb://%s:%d/%s",
                mongoConfig.net().getServerAddress().getHostAddress(),
                mongoConfig.net().getPort(),
                databaseName));

        mongoTemplate = new ReactiveMongoTemplate(new MongoClientImpl(mongo), databaseName);
    }

    /**
     * Give the Async client for embedded database
     *
     * @return an Async client
     */
    public MongoClient getMongoClient() {
        return mongo;
    }

    /**
     * Give the reactive spring template for Mong DB
     *
     * @return An async template
     */
    public ReactiveMongoTemplate getMongoTemplate() {
        return mongoTemplate;
    }

    @Override
    protected void after() {
        if (mongo != null) {
            mongo.close();
        }
        if (mongod != null) {
            mongod.stop();
        }
        if (mongodExe != null)
            mongodExe.stop();

        super.after();
    }
}
