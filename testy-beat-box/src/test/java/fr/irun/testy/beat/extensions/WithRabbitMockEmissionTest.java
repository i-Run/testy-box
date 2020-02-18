package fr.irun.testy.beat.extensions;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.irun.testy.beat.mappers.DeliveryMapper;
import fr.irun.testy.beat.mappers.JacksonDeliveryMapperFactory;
import fr.irun.testy.beat.messaging.AMQPHelper;
import fr.irun.testy.core.extensions.ChainedExtension;
import fr.irun.testy.core.extensions.WithObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import reactor.rabbitmq.SenderOptions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test case as example for an emitter.
 */
public class WithRabbitMockEmissionTest {

    private static final String RESULT_OK = "Result ok";
    private static final String QUEUE_NAME = "queueName";
    private static final String EXCHANGE_NAME = "exchangeName";
    private static final String MESSAGE_TO_SEND = "sendThisMessage";

    private static final WithObjectMapper WITH_OBJECT_MAPPER = WithObjectMapper.builder()
            .build();
    private static final WithRabbitMock WITH_RABBIT_MOCK = WithRabbitMock.builder()
            .withObjectMapper(WITH_OBJECT_MAPPER)
            .declareQueueAndExchange(QUEUE_NAME, EXCHANGE_NAME)
            .declareReplyMessage(RESULT_OK)
            .build();

    @RegisterExtension
    @SuppressWarnings("unused")
    static final ChainedExtension CHAIN = ChainedExtension.outer(WITH_OBJECT_MAPPER)
            .append(WITH_RABBIT_MOCK)
            .register();

    @Test
    void should_emit_with_reply(SenderOptions sender, ObjectMapper objectMapper) {
        final DeliveryMapper<String> responseMapper = JacksonDeliveryMapperFactory.forClass(objectMapper, String.class);

        final String actual = AMQPHelper.emitWithReply(MESSAGE_TO_SEND, sender, EXCHANGE_NAME)
                .map(responseMapper::map)
                .block();
        assertThat(actual).isEqualTo(RESULT_OK);
    }

}
