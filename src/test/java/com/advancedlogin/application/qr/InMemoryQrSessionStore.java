package com.advancedlogin.application.qr;

import com.advancedlogin.application.qr.port.out.QrSessionStore;
import com.advancedlogin.domain.qr.QrSession;
import com.advancedlogin.domain.qr.QrSessionId;
import com.advancedlogin.domain.qr.QrStatus;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Test fake with the same contract as the Redis store (synchronized = atomic CAS). No TTLs. */
class InMemoryQrSessionStore implements QrSessionStore {

    private final Map<QrSessionId, QrSession> sessions = new HashMap<>();

    @Override
    public synchronized void create(QrSession session) {
        if (sessions.putIfAbsent(session.id(), session) != null) {
            throw new IllegalStateException("exists");
        }
    }

    @Override
    public synchronized Optional<QrSession> findById(QrSessionId id) {
        return Optional.ofNullable(sessions.get(id));
    }

    @Override
    public synchronized boolean compareAndSet(QrStatus expectedStatus, QrSession updated) {
        QrSession current = sessions.get(updated.id());
        if (current == null || current.status() != expectedStatus) {
            return false;
        }
        sessions.put(updated.id(), updated);
        return true;
    }

    /** Test hook: simulate another request changing the session behind the service's back. */
    synchronized void overwrite(QrSession session) {
        sessions.put(session.id(), session);
    }
}
