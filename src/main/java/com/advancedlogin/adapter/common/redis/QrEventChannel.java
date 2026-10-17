package com.advancedlogin.adapter.common.redis;

import com.advancedlogin.domain.qr.QrSessionId;
import com.advancedlogin.domain.qr.QrStatus;
import com.advancedlogin.domain.qr.QrStatusUpdate;

import java.time.Instant;
import java.util.Optional;

/**
 * The wire contract of the Redis Pub/Sub channel {@code qr:events:{qrId}}, shared by the
 * publisher (outbound adapter) and the subscriber (inbound adapter) so the two can't drift.
 *
 * <p>Payload: {@code STATUS:expiresAtEpochMillis[:exchangeCode]}, e.g. {@code SCANNED:1792223319015}
 * or {@code CONFIRMED:1792223349015:Xy7...}. Tiny on purpose; the channel name already carries the
 * qrId. (The code is base64url, so it never contains ':'.) The code travelling through Redis is
 * fine: it's the same Redis that stores it, and SSE only forwards it to the bound browser.
 */
public final class QrEventChannel {

    public static final String PREFIX = "qr:events:";

    /** One PSUBSCRIBE per backend instance covers every QR session. */
    public static final String PATTERN = PREFIX + "*";

    private QrEventChannel() {
    }

    public static String channel(QrSessionId id) {
        return PREFIX + id.value();
    }

    public static String encode(QrStatusUpdate update) {
        String base = update.status().name() + ":" + update.expiresAt().toEpochMilli();
        return update.exchangeCode() == null ? base : base + ":" + update.exchangeCode();
    }

    /** Empty for anything malformed: a bad message is dropped, never allowed to crash the listener. */
    public static Optional<QrStatusUpdate> decode(String channel, String payload) {
        try {
            QrSessionId id = new QrSessionId(channel.substring(PREFIX.length()));
            String[] parts = payload.split(":", -1);
            if (parts.length != 2 && parts.length != 3) {
                return Optional.empty();
            }
            QrStatus status = QrStatus.valueOf(parts[0]);
            Instant expiresAt = Instant.ofEpochMilli(Long.parseLong(parts[1]));
            String exchangeCode = parts.length == 3 ? parts[2] : null;
            return Optional.of(new QrStatusUpdate(id, status, expiresAt, exchangeCode));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
