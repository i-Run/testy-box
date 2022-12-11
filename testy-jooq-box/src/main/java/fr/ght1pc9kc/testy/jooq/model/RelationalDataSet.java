package fr.ght1pc9kc.testy.jooq.model;

import org.jooq.UpdatableRecord;

import java.util.List;

public interface RelationalDataSet<T extends UpdatableRecord<T>> {
    List<T> records();
}
