package com.advancedlogin.domain.qr;

public class InvalidExchangeCodeException extends RuntimeException {

    public InvalidExchangeCodeException() {
        super("Invalid exchange code");
    }
}
