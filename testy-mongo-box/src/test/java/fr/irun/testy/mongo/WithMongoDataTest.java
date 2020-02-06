package fr.irun.testy.mongo;


import com.google.common.collect.ImmutableList;
import com.mongodb.reactivestreams.client.MongoDatabase;
import fr.irun.testy.core.extensions.ChainedExtension;
import fr.irun.testy.core.extensions.WithObjectMapper;
import fr.irun.testy.mongo.sample.DocumentDataSet;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory;

import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;

class WithMongoDataTest {

    private static final String COLLECTION_0 = "firstCollection";
    private static final String COLLECTION_1 = "secondCollection";

    private static final WithEmbeddedMongo WITH_EMBEDDED_MONGO = WithEmbeddedMongo.builder()
            .build();
    private static final WithObjectMapper WITH_OBJECT_MAPPER = WithObjectMapper.builder()
            .build();
    private static final WithMongoData WITH_MONGO_DATA = WithMongoData.builder(WITH_EMBEDDED_MONGO, WITH_OBJECT_MAPPER)
            .addDataset(COLLECTION_0, new DocumentDataSet())
            .addDataset(COLLECTION_1, new DocumentDataSet())
            .build();

    @RegisterExtension
    @SuppressWarnings("unused")
    static final ChainedExtension chain = ChainedExtension.outer(WITH_EMBEDDED_MONGO)
            .append(WITH_OBJECT_MAPPER)
            .append(WITH_MONGO_DATA)
            .register();

    private MongoDatabase mongoDatabase;

    @BeforeEach
    void setUp(ReactiveMongoDatabaseFactory mongoFactory) {
        this.mongoDatabase = mongoFactory.getMongoDatabase();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            COLLECTION_0,
            COLLECTION_1,
    })
    void should_have_inserted_data(String collectionName) throws InterruptedException {
        final TestSubscriber subscriber = new TestSubscriber(1);

        mongoDatabase.getCollection(collectionName).find()
                .subscribe(subscriber);
        subscriber.countDown.await();

        assertThat(subscriber.content.build()).containsExactly(DocumentDataSet.DOCUMENT_0, DocumentDataSet.DOCUMENT_1);
    }

    private static final class TestSubscriber implements Subscriber<Document> {

        private final CountDownLatch countDown;
        private final ImmutableList.Builder<Document> content = ImmutableList.builder();

        private TestSubscriber(int count) {
            this.countDown = new CountDownLatch(count);
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(Document o) {
            content.add(o);
        }

        @Override
        public void onError(Throwable throwable) {
            throw new IllegalStateException(throwable);
        }

        @Override
        public void onComplete() {
            countDown.countDown();
        }
    }
}