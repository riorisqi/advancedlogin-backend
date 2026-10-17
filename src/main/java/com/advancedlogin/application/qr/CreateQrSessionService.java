package com.advancedlogin.application.qr;

import com.advancedlogin.application.qr.port.in.CreateQrSessionUseCase;
import com.advancedlogin.application.qr.port.out.QrSessionStore;
import com.advancedlogin.application.qr.port.out.RandomTokenGenerator;
import com.advancedlogin.domain.qr.BindingHash;
import com.advancedlogin.domain.qr.QrSession;
import com.advancedlogin.domain.qr.QrSessionId;
import com.advancedlogin.domain.qr.RequestingDevice;

import java.time.Clock;
import java.time.Duration;

public class CreateQrSessionService implements CreateQrSessionUseCase {

    private final QrSessionStore store;
    private final RandomTokenGenerator tokens;
    private final Clock clock;
    private final Duration pendingTtl;

    public CreateQrSessionService(QrSessionStore store, RandomTokenGenerator tokens, Clock clock, Duration pendingTtl) {
        this.store = store;
        this.tokens = tokens;
        this.clock = clock;
        this.pendingTtl = pendingTtl;
    }

    @Override
    public CreatedQrSession create(RequestingDevice device) {
        // Two independent random values: the id goes into the QR (visible to anyone looking at
        // the screen), the secret goes only into this browser's HttpOnly cookie.
        QrSessionId id = new QrSessionId(tokens.newToken());
        String bindingSecret = tokens.newToken();

        QrSession session = QrSession.pending(id, BindingHash.of(bindingSecret), device, clock.instant(), pendingTtl);
        store.create(session);
        return new CreatedQrSession(session, bindingSecret);
    }
}
