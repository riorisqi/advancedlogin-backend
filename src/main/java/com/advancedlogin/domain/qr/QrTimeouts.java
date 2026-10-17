package com.advancedlogin.domain.qr;

import java.time.Duration;
import java.util.Objects;

/**
 * How long a QR session may stay in each state. Every transition restarts the clock, so a user
 * who scans at second 89 still gets the full {@code scanned} window to decide.
 *
 * @param pending   showing on the desktop, waiting for a scan
 * @param scanned   phone shows the details, waiting for approve/reject
 * @param confirmed approved, waiting for the desktop to exchange its one-time code
 * @param terminal  REJECTED (later CONSUMED) is kept this long so a polling desktop still sees it
 */
public record QrTimeouts(Duration pending, Duration scanned, Duration confirmed, Duration terminal) {

    public QrTimeouts {
        Objects.requireNonNull(pending, "pending");
        Objects.requireNonNull(scanned, "scanned");
        Objects.requireNonNull(confirmed, "confirmed");
        Objects.requireNonNull(terminal, "terminal");
    }
}
