package com.advancedlogin.application.qr.port.in;

import com.advancedlogin.domain.qr.QrSession;
import com.advancedlogin.domain.qr.RequestingDevice;

public interface CreateQrSessionUseCase {

    CreatedQrSession create(RequestingDevice device);

    /** @param bindingSecret for the qr_bind cookie; returned only here, only its hash is stored */
    record CreatedQrSession(QrSession session, String bindingSecret) {
    }
}
