package fr.ght1pc9kc.testy.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import redis.embedded.RedisServer;

@ExtendWith(WithEmbeddedRedis.class)
class WithEmbeddedRedisTest {
    @Test
    void should_use_embedded_redis(@RedisPort Integer redisPort, RedisClient client, RedisServer server) {
        Assertions.assertThat(redisPort).isPositive();
        Assertions.assertThat(client).isNotNull();
        Assertions.assertThat(server).isNotNull();

        try (StatefulRedisConnection<String, String> conn = client.connect()) {
            RedisCommands<String, String> redisCommands = conn.sync();
            String actual = redisCommands.set("key", "Hello, Redis!");
            Assertions.assertThat(actual).isEqualTo("OK");
        }
    }
}