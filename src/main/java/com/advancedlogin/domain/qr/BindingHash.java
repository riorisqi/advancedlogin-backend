package com.advancedlogin.domain.qr;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * SHA-256 of the browser-binding secret. Only the hash is stored: someone who can read Redis
 * still can't forge the {@code qr_bind} cookie.
 *
 * <p>A fast hash (not BCrypt) is fine here: the secret is 256 random bits, so there's nothing
 * to brute-force, unlike a human-chosen password.
 */
public record BindingHash(String value) {

    public BindingHash {
        Objects.requireNonNull(value, "value");
    }

    public static BindingHash of(String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            return new BindingHash(HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every JVM", e);
        }
    }

    /** Constant-time comparison: doesn't leak how many leading characters matched. */
    public boolean matches(String secret) {
        byte[] expected = value.getBytes(StandardCharsets.UTF_8);
        byte[] actual = of(secret).value().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }
}
