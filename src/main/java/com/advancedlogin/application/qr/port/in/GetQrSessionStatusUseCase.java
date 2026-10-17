package com.advancedlogin.application.qr.port.in;

import com.advancedlogin.domain.qr.QrSession;
import com.advancedlogin.domain.qr.QrSessionId;

public interface GetQrSessionStatusUseCase {

    QrSession getStatus(QrSessionId id, String bindingSecret);
}
