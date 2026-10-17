package com.advancedlogin.domain.qr;

/** A move the state machine doesn't allow (wrong state, or not the user who scanned). Maps to HTTP 409. */
public class IllegalQrTransitionException extends RuntimeException {

    public IllegalQrTransitionException(String message) {
        super(message);
    }
}
