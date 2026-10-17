package com.advancedlogin.application.qr;

/** Unknown, expired or not bound to this browser: one exception, so a response never reveals which. */
public class QrSessionNotFoundException extends RuntimeException {

    public QrSessionNotFoundException() {
        super("QR code is invalid or has expired");
    }
}
