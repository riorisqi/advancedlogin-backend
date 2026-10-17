package com.advancedlogin.adapter.out.redis;

import com.advancedlogin.adapter.common.redis.QrEventChannel;
import com.advancedlogin.application.qr.port.out.QrStatusPublisher;
import com.advancedlogin.domain.qr.QrStatusUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes to Redis instead of pushing to local SSE streams directly. The phone's approve
 * request may hit backend instance A while the desktop's stream is open on instance B; Pub/Sub
 * delivers the message to every instance, and each forwards it to its own streams.
 */
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
            // The transition is already stored; a lost notification only delays the desktop
            // until its next reconnect/poll, so don't fail the phone's request over it.
            log.warn("Could not publish QR status update {}", update.status(), e);
        }
    }
}
