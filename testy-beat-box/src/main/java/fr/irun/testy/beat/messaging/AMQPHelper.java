package fr.irun.testy.beat.messaging;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Delivery;
import com.rabbitmq.client.Envelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.rabbitmq.RabbitFlux;
import reactor.rabbitmq.ReceiverOptions;
import reactor.rabbitmq.RpcClient;
import reactor.rabbitmq.Sender;
import reactor.rabbitmq.SenderOptions;

import java.io.IOException;
import java.time.Duration;
import java.util.Queue;
import java.util.function.Supplier;

public final class AMQPHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(AMQPHelper.class);

    private static final String DEFAULT_RABBIT_REPLY_QUEUE_NAME = "amq.rabbitmq.reply-to";
    private static final int TIMEOUT_DURATION = 1;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private AMQPHelper() {
    }

    /**
     * Declare queues for communication
     *
     * @param channel           The channel object used for communication
     * @param queueName         The queue name for rabbit communication
     * @param exchangeQueueName The exchange queue name for rabbit communication
     * @throws IOException Exception if declaration failure
     */
    public static void declareAndBindQueues(Channel channel, String queueName, String exchangeQueueName) throws IOException {
        channel.queueDeclare(queueName, false, false, true, null);
        channel.exchangeDeclare(exchangeQueueName, BuiltinExchangeType.DIRECT, false, true, null);
        channel.queueBind(queueName, exchangeQueueName, "");

        channel.queueDeclare(DEFAULT_RABBIT_REPLY_QUEUE_NAME, false, false, true, null);
    }

    /**
     * Declare Receiver Options for queue communication
     *
     * @param conn      The connection to use for communication
     * @param scheduler The scheduler to use for communication
     * @return The built receiver options
     */
    public static ReceiverOptions declareReceiverOptions(Connection conn, Scheduler scheduler) {
        return new ReceiverOptions()
                .connectionMono(Mono.fromCallable(() -> conn))
                .connectionSubscriptionScheduler(scheduler);
    }

    /**
     * Declare Sender Options for queue communication
     *
     * @param conn      The connection to use for communication
     * @param channel   The channel to use for communication
     * @param scheduler The scheduler to use for communication
     * @return The built sender options
     */
    public static SenderOptions declareSenderOptions(Connection conn, Channel channel, Scheduler scheduler) {
        return new SenderOptions()
                .connectionMono(Mono.fromCallable(() -> conn))
                .channelMono(Mono.fromCallable(() -> channel))
                .resourceManagementScheduler(scheduler);
    }

    /**
     * Declare consumer to read messages received and respond a replyMessage.
     *
     * @param channel      The channel to use for communication
     * @param messages     The messages received in a Queue
     * @param queueName    The queue name for rabbit communication
     * @param replyMessage The message used to reply to sender
     */
    public static void declareConsumer(Channel channel, Queue<Delivery> messages, String queueName, Object replyMessage) {
        try {
            channel.basicConsume(queueName, true,
                    new DefaultConsumer(channel) {
                        @Override
                        public void handleDelivery(String consumerTag,
                                                   Envelope envelope,
                                                   AMQP.BasicProperties properties,
                                                   byte[] body) throws IOException {
                            messages.offer(new Delivery(envelope, properties, body));
                            ObjectMapper objectMapper = new ObjectMapper();
                            channel.basicPublish(
                                    "",
                                    properties.getReplyTo(),
                                    new AMQP.BasicProperties.Builder().correlationId(properties.getCorrelationId()).build(),
                                    objectMapper.writeValueAsBytes(replyMessage));
                        }
                    });
        } catch (IOException e) {
            LOGGER.error("Failure during message reception", e);
        }
    }

    /**
     * Declare a message to send into rabbit communication and send it
     *
     * @param message           The rabbit message to send
     * @param senderOptions     The options used to send message
     * @param exchangeQueueName The exchange queue name
     * @param idGenerator       The Supplier used to generate ids
     * @return the result of sending ReviewResultMessage
     */
    public static Mono<Delivery> emitWithReply(Object message, SenderOptions senderOptions, String exchangeQueueName, Supplier<String> idGenerator) {
        return Mono.using(() -> RabbitFlux.createSender(senderOptions),
                sender -> AMQPHelper.processEmission(message, sender, exchangeQueueName, idGenerator),
                Sender::close
        );
    }

    private static Mono<Delivery> processEmission(Object messageToSend, Sender sender, String exchangeQueueName, Supplier<String> idGenerator) {
        return Mono.just(messageToSend)
                .flatMap(message -> {
                    final RpcClient rpcClient = sender.rpcClient(
                            exchangeQueueName,
                            "",
                            idGenerator
                    );

                    Mono<Delivery> rpcMono = rpcClient.rpc(buildRpcRequest(message))
                            .timeout(Duration.ofSeconds(TIMEOUT_DURATION))
                            .doOnError(e ->
                                    LOGGER.error("ProcessEmission failure with RPC Client '{}'. {}: {}",
                                            rpcClient, e.getClass(), e.getLocalizedMessage()));
                    rpcClient.close();
                    return rpcMono;
                });
    }

    private static Mono<RpcClient.RpcRequest> buildRpcRequest(Object message) {
        return Mono.fromCallable(() -> buildRpcRequestFromContent(OBJECT_MAPPER.writeValueAsBytes(message)));
    }

    private static RpcClient.RpcRequest buildRpcRequestFromContent(byte[] content) {
        final AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                .replyTo(DEFAULT_RABBIT_REPLY_QUEUE_NAME)
                .build();
        return new RpcClient.RpcRequest(properties, content);
    }
}
