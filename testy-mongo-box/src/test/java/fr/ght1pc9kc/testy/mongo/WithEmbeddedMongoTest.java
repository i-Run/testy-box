package fr.ght1pc9kc.testy.mongo;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;


@ExtendWith(WithEmbeddedMongo.class)
class WithEmbeddedMongoTest {

    @Test
    void should_extend_with_embedded_mongo(ReactiveMongoTemplate tested, @MongoDatabaseName String dbName) {
        assertThat(tested).isNotNull();
        assertThat(dbName).isNotNull();

        final Document toInsert = new Document(Map.of(
                "foo", "oof",
                "bar", "rab"
        ));
        final Document inserted = tested.insert(toInsert, "test-collection").block();
        assertThat(inserted).isEqualTo(toInsert);
    }
}
