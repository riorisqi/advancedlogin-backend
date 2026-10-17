package com.advancedlogin.application.qr;

import com.advancedlogin.application.qr.port.in.ApproveQrSessionUseCase;
import com.advancedlogin.application.qr.port.in.GetQrSessionStatusUseCase;
import com.advancedlogin.application.qr.port.in.RejectQrSessionUseCase;
import com.advancedlogin.application.qr.port.in.ScanQrSessionUseCase;
import com.advancedlogin.application.qr.port.out.QrSessionStore;
import com.advancedlogin.domain.qr.IllegalQrTransitionException;
import com.advancedlogin.domain.qr.QrSession;
import com.advancedlogin.domain.qr.QrSessionId;
import com.advancedlogin.domain.qr.QrTimeouts;
import com.advancedlogin.domain.user.UserId;

import java.time.Clock;
import java.time.Instant;
import java.util.function.BiFunction;

/** Status reads and phone-side transitions of a QR session. */
public class QrSessionService
        implements GetQrSessionStatusUseCase, ScanQrSessionUseCase, ApproveQrSessionUseCase, RejectQrSessionUseCase {

    /** One retry is enough: after losing a race, re-reading shows the winner's state and the domain decides. */
    private static final int MAX_ATTEMPTS = 2;

    private final QrSessionStore store;
    private final Clock clock;
    private final QrTimeouts timeouts;

    public QrSessionService(QrSessionStore store, Clock clock, QrTimeouts timeouts) {
        this.store = store;
        this.clock = clock;
        this.timeouts = timeouts;
    }

    @Override
    public QrSession getStatus(QrSessionId id, String bindingSecret) {
        QrSession session = load(id);
        if (bindingSecret == null || !session.bindingHash().matches(bindingSecret)) {
            // Someone has the qrId (it's printed on screen) but not this browser's cookie.
            throw new QrSessionNotFoundException();
        }
        return session;
    }

    @Override
    public QrSession scan(QrSessionId id, UserId user) {
        return transition(id, (session, now) -> session.scan(user, now, timeouts));
    }

    @Override
    public void approve(QrSessionId id, UserId user) {
        transition(id, (session, now) -> session.approve(user, now, timeouts));
    }

    @Override
    public void reject(QrSessionId id, UserId user) {
        transition(id, (session, now) -> session.reject(user, now, timeouts));
    }

    /**
     * Read → let the domain decide → compare-and-set. If another request changed the session
     * between our read and our write, the CAS fails and we re-evaluate against the new state:
     * e.g. the losing phone in a scan race now sees SCANNED-by-someone-else and gets a 409.
     */
    private QrSession transition(QrSessionId id, BiFunction<QrSession, Instant, QrSession> change) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            QrSession current = load(id);
            QrSession updated = change.apply(current, clock.instant());
            if (updated.equals(current)) {
                return current; // idempotent no-op (same user scanning twice)
            }
            if (store.compareAndSet(current.status(), updated)) {
                return updated;
            }
        }
        throw new IllegalQrTransitionException("QR code was changed by another request, try again");
    }

    private QrSession load(QrSessionId id) {
        QrSession session = store.findById(id).orElseThrow(QrSessionNotFoundException::new);
        if (session.isExpiredAt(clock.instant())) {
            // Redis TTLs are precise, but don't rely on it: an expired session is gone.
            throw new QrSessionNotFoundException();
        }
        return session;
    }
}
