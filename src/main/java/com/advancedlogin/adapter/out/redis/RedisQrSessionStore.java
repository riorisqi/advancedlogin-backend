package com.advancedlogin.adapter.out.redis;

import com.advancedlogin.application.qr.port.out.QrSessionStore;
import com.advancedlogin.domain.qr.QrSession;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * QR sessions as Redis hashes: {@code qr:s:{qrId}} with fields status, bindHash, ip, userAgent,
 * createdAt, expiresAt (epoch millis). Redis deletes the key when the TTL runs out, which is
 * how a session becomes EXPIRED.
 */
@Component
class RedisQrSessionStore implements QrSessionStore {

    static final String KEY_PREFIX = "qr:s:";

    private static final RedisScript<Long> CREATE =
            RedisScript.of(new ClassPathResource("lua/qr_create.lua"), Long.class);

    private final StringRedisTemplate redis;

    RedisQrSessionStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void create(QrSession session) {
        long ttlMillis = Duration.between(session.createdAt(), session.expiresAt()).toMillis();
        Long created = redis.execute(CREATE, List.of(key(session)),
                Long.toString(ttlMillis),
                "status", session.status().name(),
                "bindHash", session.bindingHash().value(),
                "ip", session.requestingDevice().ipAddress(),
                "userAgent", session.requestingDevice().userAgent(),
                "createdAt", Long.toString(session.createdAt().toEpochMilli()),
                "expiresAt", Long.toString(session.expiresAt().toEpochMilli()));
        if (created == null || created != 1L) {
            // 256-bit random ids don't collide; if this ever fires, the token generator is broken.
            throw new IllegalStateException("QR session id already exists");
        }
    }

    private static String key(QrSession session) {
        return KEY_PREFIX + session.id().value();
    }
}
