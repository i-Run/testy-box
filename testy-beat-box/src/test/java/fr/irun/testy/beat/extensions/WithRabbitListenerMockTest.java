package fr.irun.testy.beat.extensions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Delivery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.RabbitFlux;
import reactor.rabbitmq.ReceiverOptions;
import reactor.rabbitmq.RpcClient;
import reactor.rabbitmq.Sender;
import reactor.rabbitmq.SenderOptions;

import java.io.IOException;
import java.time.Duration;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;

class WithRabbitListenerMockTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(WithRabbitListenerMockTest.class);
    private static final String DEFAULT_RABBIT_REPLY_QUEUE_NAME = "amq.rabbitmq.reply-to";
    private static final String RESULT_OK = "Result ok";
    private static final String QUEUE_NAME = "queueName";
    private static final String EXCHANGE_NAME = "exchangeName";
    private static final String MESSAGE_TO_SEND = "sendThisMessage";
    @RegisterExtension
    static WithRabbitListenerMock withRabbitListenerMock = WithRabbitListenerMock.builder()
            .declareQueueAndExchange(QUEUE_NAME, EXCHANGE_NAME)
            .declareReplyMessage(RESULT_OK)
            .build();
    private static ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp(Channel channel, SenderOptions sender, ReceiverOptions receiver, Queue<String> messages) {
        assertThat(channel).isInstanceOf(Channel.class);
        assertThat(sender).isInstanceOf(SenderOptions.class);
        assertThat(receiver).isInstanceOf(ReceiverOptions.class);
        assertThat(messages).isInstanceOf(Queue.class);
        assertThat(channel).isNotNull();
        assertThat(sender).isNotNull();
        assertThat(receiver).isNotNull();
        assertThat(messages).isNotNull();
    }

    @Test
    void should_inject_channel(Channel tested) {
        assertThat(tested).isInstanceOf(Channel.class);
        assertThat(tested).isNotNull();
    }

    @Test
    void should_inject_sender(SenderOptions tested) {
        assertThat(tested).isInstanceOf(SenderOptions.class);
        assertThat(tested).isNotNull();
    }

    @Test
    void should_inject_receiver(ReceiverOptions tested) {
        assertThat(tested).isInstanceOf(ReceiverOptions.class);
        assertThat(tested).isNotNull();
    }

    @Test
    void should_inject_list(Queue<Delivery> tested) {
        assertThat(tested).isInstanceOf(Queue.class);
        assertThat(tested).isNotNull();
    }

    @Test
    void should_listen_and_reply(SenderOptions senderOptions, Queue<Delivery> messages) throws IOException {
        Delivery replyMessage = emitWithReply(MESSAGE_TO_SEND, senderOptions).block();

        assert replyMessage != null;
        assertThat(objectMapper.readValue(replyMessage.getBody(), String.class))
                .isEqualTo(RESULT_OK);

        assert messages != null;
        assertThat(messages.isEmpty()).isFalse();
        assertThat(objectMapper.readValue(messages.remove().getBody(), String.class))
                .isEqualTo(MESSAGE_TO_SEND);
    }

    private Mono<Delivery> emitWithReply(Object message, SenderOptions senderOptions) {
        return Mono.using(() -> RabbitFlux.createSender(senderOptions),
                rabbitSender -> processEmission(rabbitSender, message),
                Sender::close
        );
    }

    private Mono<Delivery> processEmission(Sender sender, Object message) {
        return Mono.just(message)
                .flatMap(messageToSend -> {
                    final RpcClient rpcClient = sender.rpcClient(
                            EXCHANGE_NAME,
                            "",
                            () -> "ID" + Math.random()
                    );

                    Mono<Delivery> rpcMono = rpcClient.rpc(buildRpcRequest(messageToSend))
                            .doOnError(e ->
                                    LOGGER.error("ProcessEmission failure with RPC Client '{}'. {}: {}",
                                            rpcClient, e.getClass(), e.getLocalizedMessage()));
                    rpcClient.close();
                    return rpcMono;
                })
                .timeout(Duration.ofSeconds(1));
    }

    private Mono<RpcClient.RpcRequest> buildRpcRequest(Object message) {
        return Mono.fromCallable(() -> buildRpcRequestFromContent(objectMapper.writeValueAsBytes(message)));
    }

    private RpcClient.RpcRequest buildRpcRequestFromContent(byte[] content) {
        final AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                .replyTo(DEFAULT_RABBIT_REPLY_QUEUE_NAME)
                .build();
        return new RpcClient.RpcRequest(properties, content);
    }
}