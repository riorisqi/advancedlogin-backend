package com.advancedlogin.domain.qr;

import com.advancedlogin.domain.user.UserId;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * One QR login attempt and its state machine (docs/ARCHITECTURE.md §3).
 *
 * <p>Immutable: each transition method validates the move and returns a <em>new</em> instance
 * with a fresh expiry. Persisting it safely (only if nobody else changed the session meanwhile)
 * is the store's compare-and-set job.
 *
 * @param scannedBy the phone user who scanned it; null while PENDING. Once set, only this user
 *                  can approve or reject.
 */
public record QrSession(
        QrSessionId id,
        QrStatus status,
        BindingHash bindingHash,
        RequestingDevice requestingDevice,
        Instant createdAt,
        Instant expiresAt,
        UserId scannedBy,
        Instant scannedAt) {

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
        if ((status == QrStatus.PENDING) != (scannedBy == null)) {
            throw new IllegalArgumentException("scannedBy must be set exactly when the session has been scanned");
        }
    }

    public static QrSession pending(QrSessionId id, BindingHash bindingHash, RequestingDevice device,
                                    Instant now, QrTimeouts timeouts) {
        return new QrSession(id, QrStatus.PENDING, bindingHash, device, now, now.plus(timeouts.pending()), null, null);
    }

    public Optional<UserId> scanner() {
        return Optional.ofNullable(scannedBy);
    }

    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /**
     * PENDING → SCANNED, locked to {@code user}. Scanning again by the same user is a no-op
     * (returns this), so a double tap or a page reload on the phone doesn't fail.
     */
    public QrSession scan(UserId user, Instant now, QrTimeouts timeouts) {
        if (status == QrStatus.SCANNED && user.equals(scannedBy)) {
            return this;
        }
        requireStatus(QrStatus.PENDING, "scan");
        return withStatus(QrStatus.SCANNED, now.plus(timeouts.scanned()), user, now);
    }

    /** SCANNED → CONFIRMED, only by the user who scanned. */
    public QrSession approve(UserId user, Instant now, QrTimeouts timeouts) {
        requireScannedBy(user, "approve");
        return withStatus(QrStatus.CONFIRMED, now.plus(timeouts.confirmed()), scannedBy, scannedAt);
    }

    /** SCANNED → REJECTED, only by the user who scanned. Kept briefly so the desktop can see it. */
    public QrSession reject(UserId user, Instant now, QrTimeouts timeouts) {
        requireScannedBy(user, "reject");
        return withStatus(QrStatus.REJECTED, now.plus(timeouts.terminal()), scannedBy, scannedAt);
    }

    private void requireScannedBy(UserId user, String action) {
        requireStatus(QrStatus.SCANNED, action);
        if (!user.equals(scannedBy)) {
            throw new IllegalQrTransitionException("Only the user who scanned this QR code can " + action + " it");
        }
    }

    private void requireStatus(QrStatus expected, String action) {
        if (status != expected) {
            throw new IllegalQrTransitionException("Cannot " + action + " a QR code that is " + status);
        }
    }

    private QrSession withStatus(QrStatus newStatus, Instant newExpiresAt, UserId newScannedBy, Instant newScannedAt) {
        return new QrSession(id, newStatus, bindingHash, requestingDevice, createdAt, newExpiresAt, newScannedBy, newScannedAt);
    }
}
