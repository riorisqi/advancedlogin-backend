package com.advancedlogin.application.qr.port.in;

import com.advancedlogin.domain.qr.QrSession;
import com.advancedlogin.domain.qr.QrSessionId;
import com.advancedlogin.domain.user.UserId;

/** Phone side: a logged-in user scanned the QR. Returns the session so the phone can show who's asking. */
public interface ScanQrSessionUseCase {

    QrSession scan(QrSessionId id, UserId user);
}
