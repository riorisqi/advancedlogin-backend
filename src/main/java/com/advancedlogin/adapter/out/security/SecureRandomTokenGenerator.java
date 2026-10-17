package com.advancedlogin.adapter.out.security;

import com.advancedlogin.application.qr.port.out.RandomTokenGenerator;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * SecureRandom, not Random/UUID: java.util.Random is predictable from a few outputs, and a
 * UUID v4 has only 122 random bits and isn't specified to come from a CSPRNG.
 */
@Component
class SecureRandomTokenGenerator implements RandomTokenGenerator {

    private static final int TOKEN_BYTES = 32; // 256 bits

    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

    @Override
    public String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return encoder.encodeToString(bytes);
    }
}
