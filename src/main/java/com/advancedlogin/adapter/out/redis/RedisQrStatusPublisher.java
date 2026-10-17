package com.advancedlogin.adapter.out.redis;

import com.advancedlogin.adapter.common.redis.QrEventChannel;
import com.advancedlogin.application.qr.port.out.QrStatusPublisher;
import com.advancedlogin.domain.qr.QrStatusUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Pub/Sub, not local streams: the desktop's stream may be open on another backend instance. */
@Component
class RedisQrStatusPublisher implements QrStatusPublisher {

    private static final Logger log = LoggerFactory.getLogger(RedisQrStatusPublisher.class);

    private final StringRedisTemplate redis;

    RedisQrStatusPublisher(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void publish(QrStatusUpdate update) {
        try {
            redis.convertAndSend(QrEventChannel.channel(update.id()), QrEventChannel.encode(update));
        } catch (RuntimeException e) {
            // Already stored: a lost notification only delays the desktop until its next reconnect/poll.
            log.warn("Could not publish QR status update {}", update.status(), e);
        }
    }
}
