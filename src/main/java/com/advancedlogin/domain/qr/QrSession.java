package com.advancedlogin.domain.qr;

import com.advancedlogin.domain.user.UserId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
 * @param scannedBy    the phone user who scanned it; null while PENDING. Once set, only this user
 *                     can approve or reject, and it's the user the desktop gets logged in as.
 * @param exchangeCode one-time code the desktop trades for a real session; present only while
 *                     CONFIRMED (short-lived, and useless without the binding cookie).
 */
public record QrSession(
        QrSessionId id,
        QrStatus status,
        BindingHash bindingHash,
        RequestingDevice requestingDevice,
        Instant createdAt,
        Instant expiresAt,
        UserId scannedBy,
        Instant scannedAt,
        String exchangeCode) {

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
        if ((status == QrStatus.CONFIRMED) != (exchangeCode != null)) {
            throw new IllegalArgumentException("exchangeCode must be set exactly while CONFIRMED");
        }
    }

    public static QrSession pending(QrSessionId id, BindingHash bindingHash, RequestingDevice device,
                                    Instant now, QrTimeouts timeouts) {
        return new QrSession(id, QrStatus.PENDING, bindingHash, device, now, now.plus(timeouts.pending()),
                null, null, null);
    }

    public Optional<UserId> scanner() {
        return Optional.ofNullable(scannedBy);
    }

    public Optional<String> code() {
        return Optional.ofNullable(exchangeCode);
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
        return with(QrStatus.SCANNED, now.plus(timeouts.scanned()), user, now, null);
    }

    /** SCANNED → CONFIRMED, only by the user who scanned. Issues the one-time exchange code. */
    public QrSession approve(UserId user, String newExchangeCode, Instant now, QrTimeouts timeouts) {
        requireScannedBy(user, "approve");
        Objects.requireNonNull(newExchangeCode, "newExchangeCode");
        return with(QrStatus.CONFIRMED, now.plus(timeouts.confirmed()), scannedBy, scannedAt, newExchangeCode);
    }

    /** SCANNED → REJECTED, only by the user who scanned. Kept briefly so the desktop can see it. */
    public QrSession reject(UserId user, Instant now, QrTimeouts timeouts) {
        requireScannedBy(user, "reject");
        return with(QrStatus.REJECTED, now.plus(timeouts.terminal()), scannedBy, scannedAt, null);
    }

    /**
     * CONFIRMED → CONSUMED: the desktop redeems the code (exactly once). The caller has already
     * checked the binding cookie; this checks the code and that it hasn't been used yet.
     */
    public QrSession consume(String presentedCode, Instant now, QrTimeouts timeouts) {
        requireStatus(QrStatus.CONFIRMED, "redeem");
        if (presentedCode == null || !constantTimeEquals(exchangeCode, presentedCode)) {
            throw new InvalidExchangeCodeException();
        }
        return with(QrStatus.CONSUMED, now.plus(timeouts.terminal()), scannedBy, scannedAt, null);
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

    private QrSession with(QrStatus newStatus, Instant newExpiresAt, UserId newScannedBy, Instant newScannedAt,
                           String newExchangeCode) {
        return new QrSession(id, newStatus, bindingHash, requestingDevice, createdAt, newExpiresAt,
                newScannedBy, newScannedAt, newExchangeCode);
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String toString() {
        // Never log the exchange code.
        return "QrSession[id=%s, status=%s, expiresAt=%s]".formatted(id.value(), status, expiresAt);
    }
}
