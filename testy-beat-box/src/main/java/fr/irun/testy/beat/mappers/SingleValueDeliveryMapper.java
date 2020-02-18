package fr.irun.testy.beat.mappers;

import com.rabbitmq.client.Delivery;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Implementation of {@link DeliveryMapper} returning one value.
 * This value can be set by the tests.
 *
 * @param <T> Type of returned value.
 */
public class SingleValueDeliveryMapper<T> implements DeliveryMapper<T> {

    private final AtomicReference<T> value = new AtomicReference<>();

    /**
     * Default constructor.
     */
    public SingleValueDeliveryMapper() {
    }

    /**
     * Build and set the base value.
     *
     * @param initValue The value of the mapper.
     */
    public SingleValueDeliveryMapper(T initValue) {
        this.value.set(initValue);
    }

    /**
     * Define the value to be returned by this mapper.
     *
     * @param value The value returned by this mapper.
     */
    public void setValue(T value) {
        this.value.set(value);
    }

    @Override
    public T map(Delivery delivery) {
        return this.value.get();
    }
}
