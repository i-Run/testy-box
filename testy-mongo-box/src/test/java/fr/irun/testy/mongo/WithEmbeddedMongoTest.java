package fr.irun.testy.mongo;

import com.google.common.collect.ImmutableMap;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;

import static org.assertj.core.api.Assertions.assertThat;


@ExtendWith(WithEmbeddedMongo.class)
class WithEmbeddedMongoTest {

    @Test
    void should_extend_with_embedded_mongo(ReactiveMongoTemplate tested, @MongoDatabaseName String dbName) {
        assertThat(tested).isNotNull();
        assertThat(dbName).isNotNull();

        final Document toInsert = new Document(ImmutableMap.of(
                "foo", "oof",
                "bar", "rab"
        ));
        final Document inserted = tested.insert(toInsert, "test-collection").block();
        assertThat(inserted).isEqualTo(toInsert);
    }
}
