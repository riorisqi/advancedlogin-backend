package com.advancedlogin.application.qr.port.out;

import com.advancedlogin.domain.qr.QrSession;
import com.advancedlogin.domain.qr.QrSessionId;
import com.advancedlogin.domain.qr.QrStatus;

import java.util.Optional;

public interface QrSessionStore {

    /**
     * Stores a brand-new session that disappears on its own at {@code session.expiresAt()}.
     * Expiry is the store's job (Redis TTL), so no cleanup job is ever needed.
     */
    void create(QrSession session);

    /** Empty when the session never existed or has expired; callers can't (and needn't) tell which. */
    Optional<QrSession> findById(QrSessionId id);

    /**
     * Atomically replaces the session, <em>only if</em> it still exists and its status is still
     * {@code expectedStatus}. Returns false if someone else changed it first (or it expired).
     *
     * <p>This is optimistic concurrency: read, decide in the domain, then write-if-unchanged.
     * Two phones scanning at the same instant both read PENDING, but only one CAS succeeds.
     */
    boolean compareAndSet(QrStatus expectedStatus, QrSession updated);
}
