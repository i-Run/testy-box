package fr.ght1pc9kc.testy.beat.utils.samples;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.time.Instant;

@AllArgsConstructor
@Value
public class TestModel {

    public static final TestModel OBIWAN = new TestModel("obiwan", 5, Instant.parse("2020-02-15T00:00:00.0Z"));
    public static final TestModel ANAKIN = new TestModel("anakin", 2, Instant.parse("2020-02-21T00:00:00.0Z"));

    public final String login;
    public final int nbConnections;
    public final Instant lastConnection;

}
