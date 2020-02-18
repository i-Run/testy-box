package fr.irun.testy.beat.mappers;

import com.rabbitmq.client.Delivery;

/**
 * Map a rabbit {@link Delivery} to customized object.
 *
 * @param <T> Type of object mapped.
 */
@FunctionalInterface
public interface DeliveryMapper<T> {

    /**
     * Map a delivery.
     *
     * @param delivery The input delivery.
     * @return The mapped value.
     */
    T map(Delivery delivery);

}
