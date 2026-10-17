package com.advancedlogin.adapter.out.redis;

import com.advancedlogin.application.qr.port.out.QrSessionStore;
import com.advancedlogin.domain.qr.BindingHash;
import com.advancedlogin.domain.qr.QrSession;
import com.advancedlogin.domain.qr.QrSessionId;
import com.advancedlogin.domain.qr.QrStatus;
import com.advancedlogin.domain.qr.RequestingDevice;
import com.advancedlogin.domain.user.UserId;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Hash {@code qr:s:{qrId}}, times in epoch millis. An empty field means absent (HSET can't delete
 * one). The key's TTL is what expires a session.
 */
@Component
class RedisQrSessionStore implements QrSessionStore {

    static final String KEY_PREFIX = "qr:s:";

    private static final RedisScript<Long> CREATE =
            RedisScript.of(new ClassPathResource("lua/qr_create.lua"), Long.class);
    private static final RedisScript<Long> TRANSITION =
            RedisScript.of(new ClassPathResource("lua/qr_transition.lua"), Long.class);

    private final StringRedisTemplate redis;
    private final Clock clock;

    RedisQrSessionStore(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    @Override
    public void create(QrSession session) {
        List<String> args = new ArrayList<>();
        args.add(Long.toString(ttlMillis(session)));
        args.addAll(toFields(session));
        Long created = redis.execute(CREATE, List.of(key(session.id())), args.toArray());
        if (created == null || created != 1L) {
            // 256-bit random ids don't collide: if this fires, the token generator is broken.
            throw new IllegalStateException("QR session id already exists");
        }
    }

    @Override
    public Optional<QrSession> findById(QrSessionId id) {
        Map<Object, Object> hash = redis.opsForHash().entries(key(id));
        return hash.isEmpty() ? Optional.empty() : Optional.of(fromFields(id, hash));
    }

    @Override
    public boolean compareAndSet(QrStatus expectedStatus, QrSession updated) {
        List<String> args = new ArrayList<>();
        args.add(expectedStatus.name());
        args.add(Long.toString(ttlMillis(updated)));
        args.addAll(toFields(updated));
        Long result = redis.execute(TRANSITION, List.of(key(updated.id())), args.toArray());
        return result != null && result == 1L;
    }

    private long ttlMillis(QrSession session) {
        // Relative TTL (PEXPIRE, not PEXPIREAT), so the Redis server's clock doesn't matter.
        return Math.max(1, Duration.between(clock.instant(), session.expiresAt()).toMillis());
    }

    private static List<String> toFields(QrSession s) {
        List<String> fields = new ArrayList<>(List.of(
                "status", s.status().name(),
                "bindHash", s.bindingHash().value(),
                "ip", s.requestingDevice().ipAddress(),
                "userAgent", s.requestingDevice().userAgent(),
                "location", s.requestingDevice().approximateLocation() == null ? "" : s.requestingDevice().approximateLocation(),
                "createdAt", millis(s.createdAt()),
                "expiresAt", millis(s.expiresAt()),
                "exchangeCode", s.exchangeCode() == null ? "" : s.exchangeCode()));
        if (s.scannedBy() != null) {
            fields.addAll(List.of("scannedBy", s.scannedBy().value(), "scannedAt", millis(s.scannedAt())));
        }
        return fields;
    }

    private static QrSession fromFields(QrSessionId id, Map<Object, Object> h) {
        String scannedBy = (String) h.get("scannedBy");
        String scannedAt = (String) h.get("scannedAt");
        String exchangeCode = (String) h.get("exchangeCode");
        return new QrSession(
                id,
                QrStatus.valueOf((String) h.get("status")),
                new BindingHash((String) h.get("bindHash")),
                new RequestingDevice((String) h.get("ip"), (String) h.get("userAgent"), (String) h.get("location")),
                instant(h.get("createdAt")),
                instant(h.get("expiresAt")),
                scannedBy == null ? null : new UserId(scannedBy),
                scannedAt == null ? null : instant(scannedAt),
                exchangeCode == null || exchangeCode.isEmpty() ? null : exchangeCode);
    }

    private static String key(QrSessionId id) {
        return KEY_PREFIX + id.value();
    }

    private static String millis(Instant instant) {
        return Long.toString(instant.toEpochMilli());
    }

    private static Instant instant(Object epochMillis) {
        return Instant.ofEpochMilli(Long.parseLong((String) epochMillis));
    }
}
