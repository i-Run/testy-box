package fr.irun.testy.mongo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.mongodb.BasicDBObject;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.mongodb.reactivestreams.client.Success;
import fr.irun.testy.core.extensions.WithObjectMapper;
import org.bson.Document;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.reactivestreams.Publisher;
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Extension allowing to initialize a mongo database with data.
 * <p>
 * Example of use with model:
 * <pre>
 *
 * &#64;Builder
 * &#64;Value
 * public class User {
 *
 *     private final String id;
 *     private final String login;
 *     private final String firstName;
 *     private final String lastName;
 *     private final String password;
 *
 * }
 * </pre>
 * <p>
 * Implementation of the data set:
 * <pre>
 *
 * public class UserDataSet implements MongoDataSet&lt;User&gt; {
 *     &#64;Override
 *     public List&lt;User&gt; documents() {
 *         final User user = User.builder()
 *                 .id("generated-id")
 *                 .firstName("Obiwan")
 *                 .lastName("Kenobi")
 *                 .login("okenobi")
 *                 .password("110812f67fa1e1f0117f6f3d70241c1a42a7b07711a93c2477cc516d9042f9db")
 *                 .build();
 *         return ImmutableList.of(user);
 *     }
 * }
 * </pre>
 * <p>
 * Use of the extension. Before each test, the mongo test database will contain the documents of the data set:
 * <pre>
 * public class UserMongoRepositoryTest {
 *
 *     private static final String USER_COLLECTION = "user";
 *
 *     private static final WithEmbeddedMongo WITH_EMBEDDED_MONGO = WithEmbeddedMongo.builder()
 *             .build();
 *     private static final WithObjectMapper WITH_OBJECT_MAPPER = WithObjectMapper.builder()
 *             .addMixin(User.class, UserMongoMixin.class)
 *             .build();
 *     private static final WithMongoData WITH_MONGO_DATA = WithMongoData.builder(WITH_EMBEDDED_MONGO)
 *             .withObjectMapper(WITH_OBJECT_MAPPER)
 *             .addDataset(USER_COLLECTION, new UserDataSet())
 *             .build();
 *
 *     &#64;RegisterExtension
 *     static final ChainedExtension CHAIN = ChainedExtension.outer(WITH_EMBEDDED_MONGO)
 *             .append(WITH_OBJECT_MAPPER)
 *             .append(WITH_MONGO_DATA)
 *             .register();
 *
 *     // (...)
 * }
 * </pre>
 */
public final class WithMongoData implements BeforeEachCallback {

    private final WithEmbeddedMongo wEmbeddedMongo;
    @Nullable
    private final WithObjectMapper wObjectMapper;
    private final Map<String, MongoDataSet<?>> dataSets;

    private WithMongoData(WithEmbeddedMongo wEmbeddedMongo,
                          Map<String, MongoDataSet<?>> dataSets) {
        this.wEmbeddedMongo = wEmbeddedMongo;
        this.wObjectMapper = null;
        this.dataSets = dataSets;
    }

    private WithMongoData(WithEmbeddedMongo wEmbeddedMongo,
                          @Nonnull WithObjectMapper wObjectMapper,
                          Map<String, MongoDataSet<?>> dataSets) {
        this.wEmbeddedMongo = wEmbeddedMongo;
        this.wObjectMapper = wObjectMapper;
        this.dataSets = dataSets;
    }


    @Override
    public void beforeEach(ExtensionContext context) {
        final ReactiveMongoDatabaseFactory reactiveFactory = this.wEmbeddedMongo.getMongoFactory(context);
        final ObjectMapper objectMapper = Optional.ofNullable(this.wObjectMapper)
                .map(wom -> wom.getObjectMapper(context))
                .orElseGet(ObjectMapper::new);
        final MongoDatabase mongoDb = reactiveFactory.getMongoDatabase();

        dataSets.forEach((collection, dataSet) -> {
            cleanCollection(mongoDb, collection);
            fillCollection(mongoDb, objectMapper, collection, dataSet);
        });
    }

    private void cleanCollection(MongoDatabase mongoDb, String collectionName) {
        final Publisher<DeleteResult> publisher = mongoDb.getCollection(collectionName).deleteMany(new BasicDBObject());
        Mono.from(publisher).block();
    }

    private void fillCollection(MongoDatabase mongoDb, ObjectMapper objectMapper, String collectionName, MongoDataSet<?> dataSet) {
        final List<Document> toInsert = dataSet.documents().stream()
                .map(o -> objectMapper.convertValue(o, Document.class))
                .collect(Collectors.toList());

        final Publisher<Success> publisher = mongoDb.getCollection(collectionName).insertMany(toInsert);
        Mono.from(publisher).block();
    }

    /**
     * Create a {@link WithMongoDataBuilder} for the extension.
     *
     * @param wEmbeddedMongo Embedded mongo database.
     * @return {@link WithMongoDataBuilder}.
     */
    public static WithMongoDataBuilder builder(WithEmbeddedMongo wEmbeddedMongo) {
        return new WithMongoDataBuilder(wEmbeddedMongo);
    }

    /**
     * Builder for the extension class {@link WithMongoData}.
     */
    public static final class WithMongoDataBuilder {

        private final WithEmbeddedMongo wEmbeddedMongo;
        @Nullable
        private WithObjectMapper wObjectMapper;
        private final ImmutableMap.Builder<String, MongoDataSet<?>> dataSetsBuilder = ImmutableMap.builder();

        private WithMongoDataBuilder(WithEmbeddedMongo wEmbeddedMongo) {
            this.wEmbeddedMongo = wEmbeddedMongo;
        }

        /**
         * Add a customized object mapper to convert the objects to documents.
         *
         * @param wObjectMapper The {@link WithObjectMapper} extension to map the documents.
         * @return Builder instance.
         */
        public WithMongoDataBuilder withObjectMapper(WithObjectMapper wObjectMapper) {
            this.wObjectMapper = wObjectMapper;
            return this;
        }

        /**
         * Add a data set.
         *
         * @param collectionName Name of the collection the data-set will fill.
         * @param dataSet        Set of data to initialize the collection with.
         * @return Builder instance.
         */
        public WithMongoDataBuilder addDataset(String collectionName, MongoDataSet<?> dataSet) {
            this.dataSetsBuilder.put(collectionName, dataSet);
            return this;
        }

        /**
         * Build the extension.
         *
         * @return The built {@link WithMongoData} extension.
         */
        public WithMongoData build() {
            return Optional.ofNullable(wObjectMapper)
                    .map(wom -> new WithMongoData(wEmbeddedMongo, wom, dataSetsBuilder.build()))
                    .orElseGet(() -> new WithMongoData(wEmbeddedMongo, dataSetsBuilder.build()));
        }
    }
}
