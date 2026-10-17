package com.advancedlogin.application.qr;

import com.advancedlogin.application.auth.port.out.UserRepository;
import com.advancedlogin.application.qr.port.in.ApproveQrSessionUseCase;
import com.advancedlogin.application.qr.port.in.ExchangeQrCodeUseCase;
import com.advancedlogin.application.qr.port.in.GetQrSessionStatusUseCase;
import com.advancedlogin.application.qr.port.in.RejectQrSessionUseCase;
import com.advancedlogin.application.qr.port.in.ScanQrSessionUseCase;
import com.advancedlogin.application.qr.port.out.QrSessionStore;
import com.advancedlogin.application.qr.port.out.QrStatusPublisher;
import com.advancedlogin.application.qr.port.out.RandomTokenGenerator;
import com.advancedlogin.domain.qr.IllegalQrTransitionException;
import com.advancedlogin.domain.qr.QrSession;
import com.advancedlogin.domain.qr.QrSessionId;
import com.advancedlogin.domain.qr.QrStatusUpdate;
import com.advancedlogin.domain.qr.QrTimeouts;
import com.advancedlogin.domain.user.User;
import com.advancedlogin.domain.user.UserId;

import java.time.Clock;
import java.time.Instant;
import java.util.function.BiFunction;

public class QrSessionService
        implements GetQrSessionStatusUseCase, ScanQrSessionUseCase, ApproveQrSessionUseCase, RejectQrSessionUseCase,
        ExchangeQrCodeUseCase {

    // One retry: after losing a race, the re-read shows the winner's state and the domain decides.
    private static final int MAX_ATTEMPTS = 2;

    private final QrSessionStore store;
    private final QrStatusPublisher publisher;
    private final RandomTokenGenerator tokens;
    private final UserRepository users;
    private final Clock clock;
    private final QrTimeouts timeouts;

    public QrSessionService(QrSessionStore store, QrStatusPublisher publisher, RandomTokenGenerator tokens,
                            UserRepository users, Clock clock, QrTimeouts timeouts) {
        this.store = store;
        this.publisher = publisher;
        this.tokens = tokens;
        this.users = users;
        this.clock = clock;
        this.timeouts = timeouts;
    }

    @Override
    public QrSession getStatus(QrSessionId id, String bindingSecret) {
        return loadBound(id, bindingSecret);
    }

    @Override
    public QrSession scan(QrSessionId id, UserId user) {
        return transition(id, (session, now) -> session.scan(user, now, timeouts));
    }

    @Override
    public void approve(QrSessionId id, UserId user) {
        String exchangeCode = tokens.newToken();
        transition(id, (session, now) -> session.approve(user, exchangeCode, now, timeouts));
    }

    @Override
    public void reject(QrSessionId id, UserId user) {
        transition(id, (session, now) -> session.reject(user, now, timeouts));
    }

    @Override
    public User exchange(QrSessionId id, String bindingSecret, String exchangeCode) {
        loadBound(id, bindingSecret);
        QrSession consumed = transition(id, (session, now) -> session.consume(exchangeCode, now, timeouts));
        UserId approver = consumed.scanner().orElseThrow();
        // The account could have been deleted between approve and exchange.
        return users.findById(approver).orElseThrow(QrSessionNotFoundException::new);
    }

    /** Read → let the domain decide → compare-and-set; on a lost race, re-evaluate against the new state. */
    private QrSession transition(QrSessionId id, BiFunction<QrSession, Instant, QrSession> change) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            QrSession current = load(id);
            QrSession updated = change.apply(current, clock.instant());
            if (updated.equals(current)) {
                return current;
            }
            if (store.compareAndSet(current.status(), updated)) {
                // Only after the write succeeded: never announce a state that wasn't stored.
                publisher.publish(QrStatusUpdate.of(updated));
                return updated;
            }
        }
        throw new IllegalQrTransitionException("QR code was changed by another request, try again");
    }

    private QrSession loadBound(QrSessionId id, String bindingSecret) {
        QrSession session = load(id);
        if (bindingSecret == null || !session.bindingHash().matches(bindingSecret)) {
            throw new QrSessionNotFoundException();
        }
        return session;
    }

    private QrSession load(QrSessionId id) {
        QrSession session = store.findById(id).orElseThrow(QrSessionNotFoundException::new);
        if (session.isExpiredAt(clock.instant())) {
            throw new QrSessionNotFoundException();
        }
        return session;
    }
}
