package fr.irun.testy.beat.mappers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Factory used to build {@link DeliveryMapper} from Jackson mapper.
 */
public final class JacksonDeliveryMapperFactory {

    private JacksonDeliveryMapperFactory() {
    }

    /**
     * Build for a class.
     *
     * @param objectMapper The jackson object mapper.
     * @param clazz        Class of the mapped element.
     * @param <T>          Type of mapped element.
     * @return {@link JacksonDeliveryMapperFactory}.
     */
    public static <T> DeliveryMapper<T> forClass(ObjectMapper objectMapper, Class<? extends T> clazz) {
        return d -> {
            try {
                return objectMapper.readValue(d.getBody(), clazz);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        };
    }

    /**
     * Build for a type reference.
     *
     * @param objectMapper  The jackson object mapper.
     * @param typeReference Ref. type of the mapped element.
     * @param <T>           Type of mapped element.
     * @return {@link JacksonDeliveryMapperFactory}.
     */
    public static <T> DeliveryMapper<T> forTypeReference(ObjectMapper objectMapper, TypeReference<? extends T> typeReference) {
        return d -> {
            try {
                return objectMapper.readValue(d.getBody(), typeReference);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        };
    }
}
