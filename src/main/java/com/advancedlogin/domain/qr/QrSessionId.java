package com.advancedlogin.domain.qr;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The id encoded in the QR code. It is semi-public (anyone who can see the screen can read it),
 * so it identifies a session but never proves ownership; that's the binding secret's job.
 */
public record QrSessionId(String value) {

    /** base64url, 43 chars = 256 random bits. Rejecting anything else keeps junk out of Redis keys. */
    private static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9_-]{43}");

    public QrSessionId {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Malformed QR session id");
        }
    }
}
