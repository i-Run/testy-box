package fr.irun.testy.beat.extensions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Delivery;
import fr.irun.testy.beat.messaging.AMQPHelper;
import fr.irun.testy.beat.messaging.AMQPReceiver;
import fr.irun.testy.beat.messaging.AmqpMessage;
import fr.irun.testy.beat.messaging.MockedReceiver;
import fr.irun.testy.beat.messaging.MockedSender;
import fr.irun.testy.core.extensions.ChainedExtension;
import fr.irun.testy.core.extensions.WithObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import reactor.core.publisher.Flux;
import reactor.rabbitmq.ReceiverOptions;
import reactor.rabbitmq.SenderOptions;

import javax.inject.Named;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

import static fr.irun.testy.beat.utils.DeliveryMappingHelper.readDeliveryValue;
import static org.assertj.core.api.Assertions.assertThat;

class WithRabbitMockTest {

    private static final String QUEUE_1 = "test-queue-1";
    private static final String QUEUE_2 = "test-queue-2";

    private static final String EXCHANGE_1 = "test-exchange-1";
    private static final String EXCHANGE_2 = "test-exchange-2";

    private static final WithObjectMapper WITH_OBJECT_MAPPER = WithObjectMapper.builder()
            .build();
    private static final WithRabbitMock WITH_RABBIT_MOCK = WithRabbitMock.builder()
            .withObjectMapper(WITH_OBJECT_MAPPER)
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
    private AMQPReceiver receiverQueue1;
    private AMQPReceiver receiverQueue2;

    @BeforeEach
    void setUp(Connection connection,
               Channel channel,
               @Named(QUEUE_1) AMQPReceiver receiverQueue1,
               @Named(QUEUE_2) AMQPReceiver receiverQueue2,
               ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;

        this.connection = connection;
        this.channel = channel;
        this.receiverQueue1 = receiverQueue1;
        this.receiverQueue2 = receiverQueue2;
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
    void should_inject_amqp_receivers(@Named(QUEUE_1) AMQPReceiver receiver1,
                                      @Named(QUEUE_2) AMQPReceiver receiver2) {
        assertThat(receiver1).isNotNull();
        assertThat(receiver1.queueName).isEqualTo(QUEUE_1);
        assertThat(receiver1).isSameAs(receiverQueue1);
        assertThat(receiver2).isNotNull();
        assertThat(receiver2.queueName).isEqualTo(QUEUE_2);
        assertThat(receiver2).isSameAs(receiverQueue2);
    }

    @Test
    void should_emit_with_reply_on_queue_1(SenderOptions sender,
                                           @Named(QUEUE_1) AMQPReceiver receiver) {
        final String request = "obiwan";
        final String response = "kenobi";

        receiver.consumeAndReply(response);

        final String actualResponse = AMQPHelper.emitWithReply(request, sender, EXCHANGE_1)
                .map(d -> readDeliveryValue(d, objectMapper, String.class))
                .block();
        assertThat(actualResponse).isNotNull();
        assertThat(actualResponse).isEqualTo(response);

        final Optional<String> actualRequest = receiver.getNextMessage()
                .map(d -> readDeliveryValue(d, objectMapper, String.class));
        assertThat(actualRequest).contains(request);

        assertThat(receiver.getNextMessage()).isEmpty();
    }

    @Test
    void should_emit_with_reply_on_queue_2(SenderOptions sender,
                                           @Named(QUEUE_2) AMQPReceiver receiver) {
        final String request = "anakin";
        final String response = "skywalker";

        receiver.consumeAndReply(response);

        final String actualResponse = AMQPHelper.emitWithReply(request, sender, EXCHANGE_2)
                .map(d -> readDeliveryValue(d, objectMapper, String.class))
                .block();
        assertThat(actualResponse).isNotNull();
        assertThat(actualResponse).isEqualTo(response);

        final Optional<String> actualRequest = receiver.getNextMessage()
                .map(d -> readDeliveryValue(d, objectMapper, String.class));
        assertThat(actualRequest).contains(request);

        assertThat(receiver.getNextMessage()).isEmpty();
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