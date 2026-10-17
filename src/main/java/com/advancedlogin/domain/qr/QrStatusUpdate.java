package com.advancedlogin.domain.qr;

import java.time.Instant;
import java.util.Objects;

/**
 * "QR session X is now in status Y until Z": what the desktop is told whenever something changes.
 * A full snapshot, not a delta, so a receiver that missed earlier updates is still correct.
 *
 * @param exchangeCode present only for CONFIRMED. It only ever travels to streams/polls that
 *                     proved they hold the binding cookie.
 */
public record QrStatusUpdate(QrSessionId id, QrStatus status, Instant expiresAt, String exchangeCode) {

    public QrStatusUpdate {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public static QrStatusUpdate of(QrSession session) {
        return new QrStatusUpdate(session.id(), session.status(), session.expiresAt(), session.exchangeCode());
    }

    /** After this update nothing else will happen to the session, so a stream can be closed. */
    public boolean isFinal() {
        return status.isTerminal();
    }

    @Override
    public String toString() {
        return "QrStatusUpdate[id=%s, status=%s]".formatted(id.value(), status);
    }
}
