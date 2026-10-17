package com.advancedlogin.domain.qr;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * One QR login attempt. Immutable: a state change produces a new instance, which the store
 * persists with a compare-and-set (from slice 4 on).
 */
public record QrSession(
        QrSessionId id,
        QrStatus status,
        BindingHash bindingHash,
        RequestingDevice requestingDevice,
        Instant createdAt,
        Instant expiresAt) {

    public QrSession {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(bindingHash, "bindingHash");
        Objects.requireNonNull(requestingDevice, "requestingDevice");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
    }

    public static QrSession pending(QrSessionId id, BindingHash bindingHash, RequestingDevice device,
                                    Instant now, Duration ttl) {
        return new QrSession(id, QrStatus.PENDING, bindingHash, device, now, now.plus(ttl));
    }

    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
