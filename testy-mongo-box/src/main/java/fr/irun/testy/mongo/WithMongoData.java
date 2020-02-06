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
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

/**
 * Extension allowing to initialize a mongo database with data.
 */
public final class WithMongoData implements BeforeEachCallback {

    private final WithEmbeddedMongo wEmbeddedMongo;
    private final WithObjectMapper wObjectMapper;
    private final Map<String, MongoDataSet<?>> dataSets;

    private WithMongoData(WithEmbeddedMongo wEmbeddedMongo,
                          WithObjectMapper wObjectMapper,
                          Map<String, MongoDataSet<?>> dataSets) {
        this.wEmbeddedMongo = wEmbeddedMongo;
        this.wObjectMapper = wObjectMapper;
        this.dataSets = dataSets;
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        final ReactiveMongoDatabaseFactory reactiveFactory = this.wEmbeddedMongo.getMongoFactory(context);
        final ObjectMapper objectMapper = this.wObjectMapper.getObjectMapper(context);
        final MongoDatabase mongoDb = reactiveFactory.getMongoDatabase();

        dataSets.forEach((collection, dataSet) -> {
            cleanCollection(mongoDb, collection);
            fillCollection(mongoDb, objectMapper, collection, dataSet);
        });
    }

    private void cleanCollection(MongoDatabase mongoDb, String collectionName) {
        final Publisher<DeleteResult> publisher = mongoDb.getCollection(collectionName).deleteMany(new BasicDBObject());
        final BlockingSubscriber subscriber = new BlockingSubscriber();

        publisher.subscribe(subscriber);
        subscriber.await();
    }

    private void fillCollection(MongoDatabase mongoDb, ObjectMapper objectMapper, String collectionName, MongoDataSet<?> dataSet) {
        final List<Document> toInsert = dataSet.documents().stream()
                .map(o -> objectMapper.convertValue(o, Document.class))
                .collect(Collectors.toList());

        final Publisher<Success> publisher = mongoDb.getCollection(collectionName).insertMany(toInsert);
        final BlockingSubscriber subscriber = new BlockingSubscriber();

        publisher.subscribe(subscriber);
        subscriber.await();
    }

    /**
     * Create a {@link WithMongoDataBuilder} for the extension.
     *
     * @param wEmbeddedMongo Embedded mongo database.
     * @param wObjectMapper  Mapper to convert objects to mongo documents.
     * @return {@link WithMongoDataBuilder}.
     */
    public static WithMongoDataBuilder builder(WithEmbeddedMongo wEmbeddedMongo,
                                               WithObjectMapper wObjectMapper) {
        return new WithMongoDataBuilder(wEmbeddedMongo, wObjectMapper);
    }

    /**
     * Builder for the extension class {@link WithMongoData}.
     */
    public static final class WithMongoDataBuilder {

        private final WithEmbeddedMongo wEmbeddedMongo;
        private final WithObjectMapper wObjectMapper;
        private final ImmutableMap.Builder<String, MongoDataSet<?>> dataSetsBuilder = ImmutableMap.builder();

        private WithMongoDataBuilder(WithEmbeddedMongo wEmbeddedMongo,
                                     WithObjectMapper wObjectMapper) {
            this.wEmbeddedMongo = wEmbeddedMongo;
            this.wObjectMapper = wObjectMapper;
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
            return new WithMongoData(wEmbeddedMongo, wObjectMapper, dataSetsBuilder.build());
        }
    }

    /**
     * Blocking subscriber for mongo publishers.
     */
    private static final class BlockingSubscriber implements Subscriber<Object> {

        private final CountDownLatch countDownLatch;

        BlockingSubscriber() {
            this.countDownLatch = new CountDownLatch(1);
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(Object t) {
            // No action
        }

        @Override
        public void onError(Throwable throwable) {
            throw new IllegalStateException(throwable);
        }

        @Override
        public void onComplete() {
            this.countDownLatch.countDown();
        }

        void await() {
            try {
                this.countDownLatch.await();
            } catch (InterruptedException e) {
                throw new IllegalStateException("Mongo subscriber interrupted", e);
            }
        }
    }
}
