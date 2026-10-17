package com.advancedlogin.domain.qr;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * SHA-256 of the binding secret; only the hash is stored. A fast hash is fine here (unlike for
 * passwords): the secret is 256 random bits, nothing to brute-force.
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

    // Constant time: doesn't leak how many leading characters matched.
    public boolean matches(String secret) {
        byte[] expected = value.getBytes(StandardCharsets.UTF_8);
        byte[] actual = of(secret).value().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }
}
