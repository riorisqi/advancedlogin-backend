package com.advancedlogin.application.qr.port.in;

import com.advancedlogin.application.qr.QrSessionNotFoundException;
import com.advancedlogin.domain.qr.IllegalQrTransitionException;
import com.advancedlogin.domain.qr.InvalidExchangeCodeException;
import com.advancedlogin.domain.qr.QrSessionId;
import com.advancedlogin.domain.user.User;

/**
 * Desktop side, last step: trade the one-time code for "who should this browser be logged in as".
 * Both secrets are required: the binding cookie (proves it's the browser that showed the QR) and
 * the code (proves the phone approved).
 */
public interface ExchangeQrCodeUseCase {

    /**
     * @throws QrSessionNotFoundException   unknown, expired, or the binding secret doesn't match
     * @throws IllegalQrTransitionException not approved yet, or already redeemed
     * @throws InvalidExchangeCodeException wrong code
     */
    User exchange(QrSessionId id, String bindingSecret, String exchangeCode);
}
