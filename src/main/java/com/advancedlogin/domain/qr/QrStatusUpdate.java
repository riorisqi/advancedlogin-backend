package com.advancedlogin.domain.qr;

import java.time.Instant;
import java.util.Objects;

/**
 * "QR session X is now in status Y until Z": what the desktop is told whenever something changes.
 * A full snapshot, not a delta, so a receiver that missed earlier updates is still correct.
 */
public record QrStatusUpdate(QrSessionId id, QrStatus status, Instant expiresAt) {

    public QrStatusUpdate {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public static QrStatusUpdate of(QrSession session) {
        return new QrStatusUpdate(session.id(), session.status(), session.expiresAt());
    }

    /** After this update nothing else will happen to the session, so a stream can be closed. */
    public boolean isFinal() {
        return status.isTerminal();
    }
}
