package fr.irun.testy.beat.messaging;

import com.google.common.collect.ImmutableMap;
import lombok.Builder;
import lombok.Singular;

/**
 * Class wrapping the body and the headers of an AMQP message.
 */
@Builder
public final class AmqpMessage {

    public final byte[] body;

    @Singular
    public final ImmutableMap<String, Object> headers;

    /**
     * Create a simple response
     *
     * @param body Body of the response.
     * @return {@link AmqpMessage}.
     */
    public static AmqpMessage of(byte[] body) {
        return new AmqpMessage(body, ImmutableMap.of());
    }

}
