package fr.irun.testy.jooq.model;

import org.jooq.UpdatableRecord;

import java.util.List;

public interface RelationalDataSet<T extends UpdatableRecord<T>> {
    List<? extends UpdatableRecord<T>> records();
}
