package fr.ght1pc9kc.testy.beat.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Delivery;
import fr.ght1pc9kc.testy.beat.extensions.WithRabbitMock;
import fr.ght1pc9kc.testy.beat.utils.samples.TestModel;
import fr.ght1pc9kc.testy.core.extensions.ChainedExtension;
import fr.ght1pc9kc.testy.core.extensions.WithObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.OutboundMessage;
import reactor.rabbitmq.RabbitFlux;
import reactor.rabbitmq.RpcClient;
import reactor.rabbitmq.Sender;
import reactor.rabbitmq.SenderOptions;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class MockedReceiverTest {

    private static final String QUEUE = "test-queue";
    private static final String EXCHANGE = "test-exchange";
    private static final Duration BLOCK_TIMEOUT = Duration.ofMillis(500);

    private static final WithObjectMapper wObjectMapper = WithObjectMapper.builder()
            .addModule(new ParameterNamesModule())
            .build();
    private static final WithRabbitMock wRabbitMock = WithRabbitMock.builder()
            .declareQueueAndExchange(QUEUE, EXCHANGE)
            .build();

    @RegisterExtension
    static final ChainedExtension chain = ChainedExtension.outer(wObjectMapper)
            .append(wRabbitMock)
            .register();

    private static Sender sender;

    @BeforeAll
    static void beforeAll(SenderOptions senderOptions) {
        sender = RabbitFlux.createSender(senderOptions);
    }

    @AfterAll
    static void afterAll() {
        sender.close();
    }

    private RpcClient rpcClient;
    private ObjectMapper objectMapper;

    private MockedReceiver tested;

    @BeforeEach
    void setUp(Channel channel, ObjectMapper objectMapper) {
        this.rpcClient = new RpcClient(Mono.just(channel), EXCHANGE, "", () -> UUID.randomUUID().toString());
        this.objectMapper = objectMapper;

        tested = new MockedReceiver(channel);
    }

    @Test
    void should_capture_messages_sent_on_queue() {
        final int nbRequests = 5;

        final List<String> messages = IntStream.range(0, nbRequests)
                .mapToObj(i -> "test-message-" + i).toList();

        final Flux<Delivery> receivedMessage = tested.consume(nbRequests).on(QUEUE).start();

        messages.stream()
                .map(s -> new OutboundMessage(EXCHANGE, "", s.getBytes(StandardCharsets.UTF_8)))
                .forEach(m -> sender.send(Mono.just(m)).block());

        StepVerifier.create(receivedMessage)
                .thenConsumeWhile(delivery -> messages.contains(new String(delivery.getBody(), StandardCharsets.UTF_8)))
                .expectComplete()
                .verify(BLOCK_TIMEOUT);
    }

    @Test
    void should_respond_to_sent_message() throws IOException {
        final String request = TestModel.OBIWAN.login;
        final TestModel response = TestModel.OBIWAN;
        final String responseHeaderKey = "status";
        final int responseHeaderValue = 200;

        final Flux<Delivery> receivedMessage = tested.consumeOne().on(QUEUE)
                .thenRespond(AmqpMessage.builder()
                        .body(objectMapper.writeValueAsBytes(response))
                        .header(responseHeaderKey, responseHeaderValue)
                        .build())
                .start();

        final Delivery actualResponse = rpcClient.rpc(Mono.just(new RpcClient.RpcRequest(objectMapper.writeValueAsBytes(request))))
                .block(BLOCK_TIMEOUT);
        assertThat(actualResponse).isNotNull();
        assertThat(actualResponse.getBody()).isNotEmpty();
        assertThat(objectMapper.readValue(actualResponse.getBody(), TestModel.class)).isEqualTo(response);
        assertThat(actualResponse.getProperties()).isNotNull();
        final Map<String, Object> actualHeaders = actualResponse.getProperties().getHeaders();
        assertThat(actualHeaders).isNotNull()
                .containsEntry(responseHeaderKey, responseHeaderValue);

        final Delivery actualRequest = receivedMessage.single().block(BLOCK_TIMEOUT);
        assertThat(actualRequest).isNotNull();
        assertThat(objectMapper.readValue(actualRequest.getBody(), String.class)).isEqualTo(request);
    }

}