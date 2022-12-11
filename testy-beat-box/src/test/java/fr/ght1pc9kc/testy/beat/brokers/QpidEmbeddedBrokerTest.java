package fr.ght1pc9kc.testy.beat.brokers;

import com.rabbitmq.client.Connection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QpidEmbeddedBrokerTest {

    private QpidEmbeddedBroker tested;

    @BeforeEach
    void setUp() {
        tested = new QpidEmbeddedBroker();
    }

    @AfterEach
    void tearDown() {
        tested.stop();
    }

    @Test
    void should_open_connection_if_started() throws IOException {
        tested.start();

        final Connection actualConnection = tested.newConnection();
        assertThat(actualConnection.isOpen()).isTrue();

        actualConnection.close();
    }

    @Test
    void should_fail_to_open_connection_if_not_started() {
        assertThatThrownBy(tested::newConnection)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Error when opening connection to embedded broker")
                .hasCauseInstanceOf(IOException.class);
    }
}