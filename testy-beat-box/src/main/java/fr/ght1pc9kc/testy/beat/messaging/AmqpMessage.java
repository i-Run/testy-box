package fr.ght1pc9kc.testy.beat.messaging;

import lombok.Builder;
import lombok.Singular;

import java.util.Map;

/**
 * Class wrapping the body and the headers of an AMQP message.
 */
@Builder
public final class AmqpMessage {

    public final byte[] body;

    @Singular
    public final Map<String, Object> headers;

    /**
     * Create a simple response
     *
     * @param body Body of the response.
     * @return {@link AmqpMessage}.
     */
    public static AmqpMessage of(byte[] body) {
        return new AmqpMessage(body, Map.of());
    }

}
