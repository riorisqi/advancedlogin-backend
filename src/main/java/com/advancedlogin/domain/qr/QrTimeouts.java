package com.advancedlogin.domain.qr;

import java.time.Duration;
import java.util.Objects;

/**
 * Lifetime of each state; every transition restarts the clock.
 *
 * @param terminal how long REJECTED/CONSUMED stay visible to a polling desktop
 */
public record QrTimeouts(Duration pending, Duration scanned, Duration confirmed, Duration terminal) {

    public QrTimeouts {
        Objects.requireNonNull(pending, "pending");
        Objects.requireNonNull(scanned, "scanned");
        Objects.requireNonNull(confirmed, "confirmed");
        Objects.requireNonNull(terminal, "terminal");
    }
}
