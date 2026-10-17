package com.advancedlogin.application.qr.port.out;

import com.advancedlogin.domain.qr.QrSession;

public interface QrSessionStore {

    /**
     * Stores a brand-new session that disappears on its own at {@code session.expiresAt()}.
     * Expiry is the store's job (Redis TTL), so no cleanup job is ever needed.
     */
    void create(QrSession session);
}
