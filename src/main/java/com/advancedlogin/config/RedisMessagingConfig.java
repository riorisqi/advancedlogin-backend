package com.advancedlogin.config;

import com.advancedlogin.adapter.common.redis.QrEventChannel;
import com.advancedlogin.adapter.in.redis.RedisQrEventSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Pub/Sub wiring. The container holds one dedicated Redis connection in SUBSCRIBE mode (a
 * subscribed connection can't run normal commands) and dispatches messages to the listener.
 *
 * <p>{@code @EnableScheduling} drives the SSE heartbeat in QrStatusStreams.
 */
@Configuration
@EnableScheduling
class RedisMessagingConfig {

    @Bean
    RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory,
                                                                RedisQrEventSubscriber subscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, new PatternTopic(QrEventChannel.PATTERN));
        return container;
    }
}
