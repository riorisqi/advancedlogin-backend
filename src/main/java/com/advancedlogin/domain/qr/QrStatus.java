package com.advancedlogin.domain.qr;

/** See the state machine in docs/ARCHITECTURE.md §3. */
public enum QrStatus {
    PENDING,
    SCANNED,
    CONFIRMED,
    REJECTED,
    CONSUMED,
    EXPIRED;

    public boolean isTerminal() {
        return this == REJECTED || this == CONSUMED || this == EXPIRED;
    }
}
