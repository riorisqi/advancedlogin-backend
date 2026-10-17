package com.advancedlogin.adapter.in.web.qr;

import com.advancedlogin.application.qr.QrSessionNotFoundException;
import com.advancedlogin.domain.qr.QrSessionId;

final class QrIds {

    private QrIds() {
    }

    /** A malformed id gets the same 404 as an expired one: nothing to learn from the difference. */
    static QrSessionId parse(String raw) {
        try {
            return new QrSessionId(raw);
        } catch (IllegalArgumentException e) {
            throw new QrSessionNotFoundException();
        }
    }
}
