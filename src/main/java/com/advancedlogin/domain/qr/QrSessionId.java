package com.advancedlogin.domain.qr;

import java.util.Objects;
import java.util.regex.Pattern;

/** Semi-public (anyone who sees the screen can read it): it identifies a session, never proves ownership. */
public record QrSessionId(String value) {

    // base64url, 43 chars = 256 bits; anything else is rejected before it reaches a Redis key.
    private static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9_-]{43}");

    public QrSessionId {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Malformed QR session id");
        }
    }
}
