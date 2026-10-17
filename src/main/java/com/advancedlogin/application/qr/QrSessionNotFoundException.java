package com.advancedlogin.application.qr;

/**
 * Unknown, expired, or (for the desktop) not bound to this browser. Deliberately one exception
 * for all three, so a response never reveals which. Maps to HTTP 404.
 */
public class QrSessionNotFoundException extends RuntimeException {

    public QrSessionNotFoundException() {
        super("QR code is invalid or has expired");
    }
}
