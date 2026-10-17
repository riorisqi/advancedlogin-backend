package com.advancedlogin.domain.qr;

import java.time.Instant;
import java.util.Objects;

/** A full snapshot, not a delta, so a receiver that missed earlier updates is still correct. */
public record QrStatusUpdate(QrSessionId id, QrStatus status, Instant expiresAt, String exchangeCode) {

    public QrStatusUpdate {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public static QrStatusUpdate of(QrSession session) {
        return new QrStatusUpdate(session.id(), session.status(), session.expiresAt(), session.exchangeCode());
    }

    public boolean isFinal() {
        return status.isTerminal();
    }

    @Override
    public String toString() {
        return "QrStatusUpdate[id=%s, status=%s]".formatted(id.value(), status);
    }
}
