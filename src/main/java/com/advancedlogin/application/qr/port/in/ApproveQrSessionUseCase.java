package com.advancedlogin.application.qr.port.in;

import com.advancedlogin.domain.qr.QrSessionId;
import com.advancedlogin.domain.user.UserId;

public interface ApproveQrSessionUseCase {

    void approve(QrSessionId id, UserId user);
}
