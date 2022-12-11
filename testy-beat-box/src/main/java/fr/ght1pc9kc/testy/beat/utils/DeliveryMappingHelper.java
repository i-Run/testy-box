package fr.ght1pc9kc.testy.beat.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Delivery;

import java.io.IOException;

/**
 * Utility class used to convert the body of AMQP {@link Delivery} to POJO and reverse.
 * This class is implemented to avoid catching {@link IOException} in all the test classes.
 */
public final class DeliveryMappingHelper {

    private DeliveryMappingHelper() {
    }

    /**
     * Map a delivery to java Object.
     *
     * @param delivery     Delivery to map.
     * @param objectMapper Object mapper to perform the conversion.
     * @param clazz        Class of the target object.
     * @param <T>          Type of the target object.
     * @return The converted object.
     */
    public static <T> T readDeliveryValue(Delivery delivery, ObjectMapper objectMapper, Class<? extends T> clazz) {
        try {
            return objectMapper.readValue(delivery.getBody(), clazz);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }


    /**
     * Map a delivery to java Object.
     *
     * @param delivery      Delivery to map.
     * @param objectMapper  Object mapper to perform the conversion.
     * @param typeReference Type reference of the target object.
     * @param <T>           Type of the target object.
     * @return The converted object.
     */
    public static <T> T readDeliveryValue(Delivery delivery, ObjectMapper objectMapper, TypeReference<? extends T> typeReference) {
        try {
            return objectMapper.readValue(delivery.getBody(), typeReference);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Write an object as bytes.
     *
     * @param toWrite      The object to  get the bytes.
     * @param objectMapper The mapper to convert the object.
     * @return The object as bytes.
     */
    public static byte[] writeObjectAsByte(Object toWrite, ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsBytes(toWrite);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }


}
