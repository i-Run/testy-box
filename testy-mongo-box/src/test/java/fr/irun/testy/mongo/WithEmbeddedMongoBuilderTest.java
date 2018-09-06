package fr.irun.testy.mongo;

import com.google.common.collect.ImmutableMap;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.Success;
import fr.irun.testy.mongo.MongoDatabaseName;
import fr.irun.testy.mongo.WithEmbeddedMongo;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class WithEmbeddedMongoBuilderTest {

    @RegisterExtension
    static WithEmbeddedMongo wMongo = WithEmbeddedMongo.builder()
            .setDatabaseName("dummy")
            .build();

    @Test
    void should_extend_with_embedded_mongo(MongoClient tested, @MongoDatabaseName String dbName) {
        assertNotNull(tested);
        assertNotNull(dbName);

        Publisher<Success> publisher = tested.getDatabase(dbName).getCollection("dummy")
                .insertOne(new Document(ImmutableMap.of(
                        "foo", "oof",
                        "bar", "rab"
                )));
        Success actual = Mono.from(publisher).block();
        assertNotNull(actual);
    }
}
