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
 * <p>Payload: {@code STATUS:expiresAtEpochMillis}, e.g. {@code SCANNED:1792223319015}.
 * Tiny on purpose; the channel name already carries the qrId.
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
        return update.status().name() + ":" + update.expiresAt().toEpochMilli();
    }

    /** Empty for anything malformed: a bad message is dropped, never allowed to crash the listener. */
    public static Optional<QrStatusUpdate> decode(String channel, String payload) {
        try {
            QrSessionId id = new QrSessionId(channel.substring(PREFIX.length()));
            int sep = payload.indexOf(':');
            QrStatus status = QrStatus.valueOf(payload.substring(0, sep));
            Instant expiresAt = Instant.ofEpochMilli(Long.parseLong(payload.substring(sep + 1)));
            return Optional.of(new QrStatusUpdate(id, status, expiresAt));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
