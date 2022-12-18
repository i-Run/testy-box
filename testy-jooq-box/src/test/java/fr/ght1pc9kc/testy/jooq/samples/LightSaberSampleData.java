package fr.ght1pc9kc.testy.jooq.samples;

import fr.ght1pc9kc.testy.dsl.public_.tables.records.LightSaberRecord;
import fr.ght1pc9kc.testy.jooq.model.RelationalDataSet;

import java.util.List;

import static fr.ght1pc9kc.testy.dsl.public_.tables.LightSaber.LIGHT_SABER;

public class LightSaberSampleData implements RelationalDataSet<LightSaberRecord> {
    public final static LightSaberSampleData DATASET = new LightSaberSampleData();

    private static final LightSaberRecord OBIWAN_SABER = LIGHT_SABER.newRecord()
            .setOwner("Obiwan")
            .setColor("BLUE")
            .setDescription("Pretty blue Light Saber from light side");
    private static final LightSaberRecord VADER_SABER = LIGHT_SABER.newRecord()
            .setOwner("Vader")
            .setColor("RED")
            .setDescription("Obscur dark saber from dark side");

    @Override
    public List<LightSaberRecord> records() {
        return List.of(OBIWAN_SABER, VADER_SABER);
    }
}
