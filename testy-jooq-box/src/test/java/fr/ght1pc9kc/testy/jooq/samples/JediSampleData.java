package fr.ght1pc9kc.testy.jooq.samples;

import fr.ght1pc9kc.testy.dsl.public_.tables.records.JediRecord;
import fr.ght1pc9kc.testy.jooq.model.RelationalDataSet;

import java.util.List;

import static fr.ght1pc9kc.testy.dsl.public_.tables.Jedi.JEDI;

public class JediSampleData implements RelationalDataSet<JediRecord> {
    public static final JediSampleData DATASET = new JediSampleData();
    public static final JediRecord OBIWAN = JEDI.newRecord()
            .setFirstName("Obiwan")
            .setLastName("Kenobi")
            .setForceSide("LIGHT");
    public static final JediRecord YODA = JEDI.newRecord()
            .setFirstName("Yoda")
            .setLastName("Master")
            .setForceSide("LIGHT");
    public static final JediRecord VADER = JEDI.newRecord()
            .setFirstName("Vader")
            .setLastName("Dark")
            .setForceSide("OBSCUR");

    @Override
    public List<JediRecord> records() {
        return List.of(OBIWAN, YODA, VADER);
    }
}
