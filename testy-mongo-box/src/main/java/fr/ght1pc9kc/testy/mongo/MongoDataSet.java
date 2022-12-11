package fr.ght1pc9kc.testy.mongo;

import java.util.List;

/**
 * Set of documents used to initialize a collection with data.
 *
 * @param <T> Type of the elements to insert as documents.
 */
@FunctionalInterface
public interface MongoDataSet<T> {

    /**
     * Obtain the documents to insert.
     *
     * @return Documents to insert into the collection.
     */
    List<T> documents();

}
