package com.advancedlogin.adapter.out.redis;

import com.advancedlogin.application.health.port.out.DataStoreHealthPort;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

@Component
class RedisDataStoreHealthAdapter implements DataStoreHealthPort {

    private final RedisConnectionFactory redisConnectionFactory;

    RedisDataStoreHealthAdapter(RedisConnectionFactory redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @Override
    public boolean isReachable() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            return "PONG".equalsIgnoreCase(connection.ping());
        } catch (Exception e) {
            return false;
        }
    }
}
