package fr.ght1pc9kc.testy.mongo;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;


class WithEmbeddedMongoBuilderTest {

    private static final String DATABASE = "dummy";

    @RegisterExtension
    @SuppressWarnings("unused")
    static WithEmbeddedMongo wMongo = WithEmbeddedMongo.builder()
            .setDatabaseName(DATABASE)
            .build();

    @Test
    void should_extend_with_embedded_mongo(ReactiveMongoTemplate tested, @MongoDatabaseName String dbName) {
        assertThat(tested).isNotNull();
        assertThat(dbName).isEqualTo(DATABASE);

        final Document toInsert = new Document(Map.of(
                "foo", "oof",
                "bar", "rab"
        ));

        final Document inserted = tested.insert(toInsert, "dummy").block();
        assertThat(inserted).isEqualTo(toInsert);
    }
}
