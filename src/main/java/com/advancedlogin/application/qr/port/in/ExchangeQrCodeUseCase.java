package com.advancedlogin.application.qr.port.in;

import com.advancedlogin.domain.qr.QrSessionId;
import com.advancedlogin.domain.user.User;

/** Needs both secrets: the binding cookie (the browser that showed the QR) and the code (the phone approved). */
public interface ExchangeQrCodeUseCase {

    User exchange(QrSessionId id, String bindingSecret, String exchangeCode);
}
