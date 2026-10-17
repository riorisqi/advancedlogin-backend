package com.advancedlogin.application.qr.port.in;

import com.advancedlogin.application.qr.QrSessionNotFoundException;
import com.advancedlogin.domain.qr.QrSession;
import com.advancedlogin.domain.qr.QrSessionId;

/** Desktop side: "what's happening with my QR?" Only the browser holding the binding secret may ask. */
public interface GetQrSessionStatusUseCase {

    /** @throws QrSessionNotFoundException if missing, expired, or {@code bindingSecret} doesn't match */
    QrSession getStatus(QrSessionId id, String bindingSecret);
}
