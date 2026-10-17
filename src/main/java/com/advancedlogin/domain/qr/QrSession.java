package com.advancedlogin.domain.qr;

import com.advancedlogin.domain.user.UserId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * One QR login attempt and its state machine (docs/ARCHITECTURE.md §3). Transitions return a new
 * instance; the store persists it with compare-and-set.
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

    /** Same user scanning again is a no-op, so a double tap or reload on the phone doesn't fail. */
    public QrSession scan(UserId user, Instant now, QrTimeouts timeouts) {
        if (status == QrStatus.SCANNED && user.equals(scannedBy)) {
            return this;
        }
        requireStatus(QrStatus.PENDING, "scan");
        return with(QrStatus.SCANNED, now.plus(timeouts.scanned()), user, now, null);
    }

    public QrSession approve(UserId user, String newExchangeCode, Instant now, QrTimeouts timeouts) {
        requireScannedBy(user, "approve");
        Objects.requireNonNull(newExchangeCode, "newExchangeCode");
        return with(QrStatus.CONFIRMED, now.plus(timeouts.confirmed()), scannedBy, scannedAt, newExchangeCode);
    }

    public QrSession reject(UserId user, Instant now, QrTimeouts timeouts) {
        requireScannedBy(user, "reject");
        return with(QrStatus.REJECTED, now.plus(timeouts.terminal()), scannedBy, scannedAt, null);
    }

    /** The caller has already checked the binding cookie. */
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
