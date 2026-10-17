package com.advancedlogin.application.qr.port.in;

import com.advancedlogin.domain.qr.QrSession;
import com.advancedlogin.domain.qr.RequestingDevice;

public interface CreateQrSessionUseCase {

    CreatedQrSession create(RequestingDevice device);

    /**
     * @param bindingSecret the raw secret for the {@code qr_bind} cookie. Returned exactly once,
     *                      here; only its hash is stored.
     */
    record CreatedQrSession(QrSession session, String bindingSecret) {
    }
}
