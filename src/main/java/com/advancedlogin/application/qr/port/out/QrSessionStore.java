package com.advancedlogin.application.qr.port.out;

import com.advancedlogin.domain.qr.QrSession;
import com.advancedlogin.domain.qr.QrSessionId;
import com.advancedlogin.domain.qr.QrStatus;

import java.util.Optional;

public interface QrSessionStore {

    /** The session must disappear on its own at {@code expiresAt} (Redis TTL), so no cleanup job is needed. */
    void create(QrSession session);

    Optional<QrSession> findById(QrSessionId id);

    /** Atomically replaces the session only if its status is still {@code expectedStatus}; false otherwise. */
    boolean compareAndSet(QrStatus expectedStatus, QrSession updated);
}
