package com.advancedlogin.application.qr;

import com.advancedlogin.application.qr.port.in.CreateQrSessionUseCase;
import com.advancedlogin.application.qr.port.out.IpLocationLookup;
import com.advancedlogin.application.qr.port.out.QrSessionStore;
import com.advancedlogin.application.qr.port.out.RandomTokenGenerator;
import com.advancedlogin.domain.qr.BindingHash;
import com.advancedlogin.domain.qr.QrSession;
import com.advancedlogin.domain.qr.QrSessionId;
import com.advancedlogin.domain.qr.QrTimeouts;
import com.advancedlogin.domain.qr.RequestingDevice;

import java.time.Clock;

public class CreateQrSessionService implements CreateQrSessionUseCase {

    private final QrSessionStore store;
    private final RandomTokenGenerator tokens;
    private final IpLocationLookup locations;
    private final Clock clock;
    private final QrTimeouts timeouts;

    public CreateQrSessionService(QrSessionStore store, RandomTokenGenerator tokens, IpLocationLookup locations,
                                  Clock clock, QrTimeouts timeouts) {
        this.store = store;
        this.tokens = tokens;
        this.locations = locations;
        this.clock = clock;
        this.timeouts = timeouts;
    }

    @Override
    public CreatedQrSession create(RequestingDevice device) {
        // The id goes into the QR (visible on screen); the secret only into this browser's HttpOnly cookie.
        QrSessionId id = new QrSessionId(tokens.newToken());
        String bindingSecret = tokens.newToken();

        RequestingDevice located = device.withLocation(locations.approximateLocation(device.ipAddress()).orElse(null));

        QrSession session = QrSession.pending(id, BindingHash.of(bindingSecret), located, clock.instant(), timeouts);
        store.create(session);
        return new CreatedQrSession(session, bindingSecret);
    }
}
