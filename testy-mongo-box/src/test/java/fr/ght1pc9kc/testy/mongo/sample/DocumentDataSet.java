package fr.ght1pc9kc.testy.mongo.sample;

import fr.ght1pc9kc.testy.mongo.MongoDataSet;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.List;
import java.util.Map;

public class DocumentDataSet implements MongoDataSet<Document> {

    public static final Document DOCUMENT_0 = new Document(Map.of(
            "_id", "DT2020020411054699139268807960",
            "name", "Test document 0",
            "description", "This is the document 0 for test"
    ));

    public static final Document DOCUMENT_1 = new Document(Map.of(
            "_id", "DT2020020411444537684482149860",
            "name", "Test document 1",
            "description", "This is the document 1 for test"
    ));

    public static final Document DOCUMENT_WITH_MONGO_ID = new Document(Map.of(
            "_id", new ObjectId("5f738d9dbfb4983a11548c94"),
            "name", "Test document 3",
            "description", "This is the document 3 for test"
    ));

    @Override
    public List<Document> documents() {
        return List.of(
                DOCUMENT_0,
                DOCUMENT_1,
                DOCUMENT_WITH_MONGO_ID
        );
    }
}
