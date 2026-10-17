package com.advancedlogin.domain.qr;

/** The desktop presented a wrong exchange code. Maps to HTTP 400. */
public class InvalidExchangeCodeException extends RuntimeException {

    public InvalidExchangeCodeException() {
        super("Invalid exchange code");
    }
}
