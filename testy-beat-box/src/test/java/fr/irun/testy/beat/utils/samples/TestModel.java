package fr.irun.testy.beat.utils.samples;

import java.time.Instant;
import java.util.Objects;

public class TestModel {

    public static final TestModel OBIWAN = new TestModel("obiwan", 5, Instant.parse("2020-02-15T00:00:00.0Z"));
    public static final TestModel ANAKIN = new TestModel("anakin", 2, Instant.parse("2020-02-21T00:00:00.0Z"));

    public final String login;
    public final int nbConnections;
    public final Instant lastConnection;

    public TestModel(String login, int nbConnections, Instant lastConnection) {
        this.login = login;
        this.nbConnections = nbConnections;
        this.lastConnection = lastConnection;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TestModel testModel = (TestModel) o;
        return nbConnections == testModel.nbConnections
                && Objects.equals(login, testModel.login)
                && Objects.equals(lastConnection, testModel.lastConnection);
    }

    @Override
    public int hashCode() {
        return Objects.hash(login, nbConnections);
    }

    @Override
    public String toString() {
        return "TestModel{"
                + "login='" + login + '\''
                + ", nbConnections=" + nbConnections
                + ", nbConnections=" + nbConnections
                + '}';
    }
}
