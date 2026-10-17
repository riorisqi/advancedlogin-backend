package com.advancedlogin.application.qr.port.in;

import com.advancedlogin.domain.qr.QrSession;
import com.advancedlogin.domain.qr.QrSessionId;
import com.advancedlogin.domain.user.UserId;

public interface ScanQrSessionUseCase {

    QrSession scan(QrSessionId id, UserId user);
}
