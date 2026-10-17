package com.advancedlogin.adapter.common.redis;

import com.advancedlogin.domain.qr.QrSessionId;
import com.advancedlogin.domain.qr.QrStatus;
import com.advancedlogin.domain.qr.QrStatusUpdate;

import java.time.Instant;
import java.util.Optional;

/**
 * Wire format of the Pub/Sub channel {@code qr:events:{qrId}}, shared by publisher and subscriber.
 * Payload: {@code STATUS:expiresAtEpochMillis[:exchangeCode]} (base64url codes never contain ':').
 */
public final class QrEventChannel {

    public static final String PREFIX = "qr:events:";

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

    /** Empty for malformed messages: drop them rather than crash the listener. */
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
