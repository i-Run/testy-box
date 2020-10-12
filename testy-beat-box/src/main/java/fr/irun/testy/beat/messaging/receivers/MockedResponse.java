package fr.irun.testy.beat.messaging.receivers;

import com.google.common.collect.ImmutableMap;
import lombok.Builder;
import lombok.Singular;

/**
 * Class used to define the response to be returned by {@link MockedReceiverFactory}.
 */
@Builder
public final class MockedResponse {

    public final byte[] body;

    @Singular
    public final ImmutableMap<String, Object> headers;

    /**
     * Create a simple response
     *
     * @param body Body of the response.
     * @return {@link MockedResponse}.
     */
    public static MockedResponse of(byte[] body) {
        return new MockedResponse(body, ImmutableMap.of());
    }

}
