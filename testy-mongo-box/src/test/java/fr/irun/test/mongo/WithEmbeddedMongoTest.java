package fr.irun.test.mongo;

import com.google.common.collect.ImmutableMap;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.Success;
import org.bson.Document;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@Nested
@ExtendWith(WithEmbeddedMongo.class)
class WithEmbeddedMongoTest {
    @Test
    void should_extend_with_embedded_mongo(MongoClient tested, @MongoDatabaseName String dbName) {
        assertNotNull(tested);
        assertNotNull(dbName);

        Publisher<Success> publisher = tested.getDatabase(dbName).getCollection("test-collection")
                .insertOne(new Document(ImmutableMap.of(
                        "foo", "oof",
                        "bar", "rab"
                )));
        Success actual = Mono.from(publisher).block();
        assertNotNull(actual);
    }
}
