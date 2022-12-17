package fr.ght1pc9kc.testy.beat.extensions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Delivery;
import fr.ght1pc9kc.testy.beat.messaging.AMQPHelper;
import fr.ght1pc9kc.testy.beat.messaging.AmqpMessage;
import fr.ght1pc9kc.testy.beat.messaging.MockedReceiver;
import fr.ght1pc9kc.testy.beat.messaging.MockedSender;
import fr.ght1pc9kc.testy.core.extensions.ChainedExtension;
import fr.ght1pc9kc.testy.core.extensions.WithObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.ReceiverOptions;
import reactor.rabbitmq.SenderOptions;
import reactor.test.StepVerifier;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static fr.ght1pc9kc.testy.beat.utils.DeliveryMappingHelper.readDeliveryValue;
import static org.assertj.core.api.Assertions.assertThat;

class WithRabbitMockTest {

    private static final String QUEUE_1 = "test-queue-1";
    private static final String QUEUE_2 = "test-queue-2";

    private static final String EXCHANGE_1 = "test-exchange-1";
    private static final String EXCHANGE_2 = "test-exchange-2";

    private static final WithObjectMapper WITH_OBJECT_MAPPER = WithObjectMapper.builder().build();
    private static final WithRabbitMock WITH_RABBIT_MOCK = WithRabbitMock.builder()
            .declareQueueAndExchange(QUEUE_1, EXCHANGE_1)
            .declareQueueAndExchange(QUEUE_2, EXCHANGE_2)
            .build();

    @RegisterExtension
    @SuppressWarnings("unused")
    static final ChainedExtension CHAIN = ChainedExtension.outer(WITH_OBJECT_MAPPER)
            .append(WITH_RABBIT_MOCK)
            .register();

    private static SenderOptions senderOptions;
    private static ReceiverOptions receiverOptions;

    private ObjectMapper objectMapper;

    @BeforeAll
    static void beforeAll(SenderOptions senderOptions,
                          ReceiverOptions receiverOptions) {
        WithRabbitMockTest.senderOptions = senderOptions;
        WithRabbitMockTest.receiverOptions = receiverOptions;
    }

    private Connection connection;
    private Channel channel;

    @BeforeEach
    void setUp(Connection connection,
               Channel channel,
               ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;

        this.connection = connection;
        this.channel = channel;
    }

    @Test
    void should_inject_connection(Connection tested) {
        assertThat(tested).isNotNull();
        assertThat(tested.isOpen()).isTrue();
        assertThat(tested).isSameAs(connection);
    }

    @Test
    void should_inject_channel(Channel tested) {
        assertThat(tested).isNotNull();
        assertThat(tested.isOpen()).isTrue();
        assertThat(tested).isSameAs(channel);
    }

    @Test
    void should_inject_sender_options(SenderOptions tested) {
        assertThat(tested).isSameAs(senderOptions);
    }

    @Test
    void should_inject_receiver_options(ReceiverOptions tested) {
        assertThat(tested).isSameAs(receiverOptions);
    }

    @Test
    void should_inject_mock_receiver(MockedReceiver actual) {
        assertThat(actual).isNotNull()
                .isInstanceOf(MockedReceiver.class);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "obiwan | kenobi    | " + QUEUE_1 + " | " + EXCHANGE_1,
            "anakin | skywalker | " + QUEUE_2 + " | " + EXCHANGE_2,
    })
    void should_emit_with_reply_on_queue_1(
            String request, String response, String queue, String exchange,
            SenderOptions sender, MockedReceiver receiver) throws JsonProcessingException {

        final byte[] respBytes = objectMapper.writeValueAsBytes(response);

        Flux<Delivery> actual = receiver.consumeOne().on(queue)
                .thenRespond(AmqpMessage.of(respBytes))
                .start();

        Mono<String> expected = AMQPHelper.emitWithReply(request, sender, exchange)
                .map(d -> readDeliveryValue(d, objectMapper, String.class));

        StepVerifier.create(expected)
                .assertNext(e -> assertThat(e).isNotNull().isEqualTo(response))
                .verifyComplete();

        StepVerifier.create(actual)
                .assertNext(d -> assertThat(readDeliveryValue(d, objectMapper, String.class)).contains(request))
                .verifyComplete();
    }

    @Test
    void should_inject_mocked_sender_and_receiver(MockedReceiver mockedReceiver,
                                                  MockedSender mockedSender) {
        final String request = "test-request";
        final String response = "test-response";

        final Charset encoding = StandardCharsets.UTF_8;
        final Duration timeout = Duration.ofMillis(400);

        final Flux<Delivery> receivedMessages = mockedReceiver.consumeOne()
                .on(QUEUE_1)
                .thenRespond(AmqpMessage.of(response.getBytes(encoding)))
                .start();

        final String actualResponse = mockedSender.rpc(AmqpMessage.of(request.getBytes(encoding)))
                .on(EXCHANGE_1, "")
                .map(Delivery::getBody)
                .map(b -> new String(b, encoding))
                .block(timeout);
        assertThat(actualResponse).isEqualTo(response);

        final String actualRequest = receivedMessages
                .single()
                .map(Delivery::getBody)
                .map(b -> new String(b, encoding))
                .block(timeout);
        assertThat(actualRequest).isEqualTo(request);
    }
}