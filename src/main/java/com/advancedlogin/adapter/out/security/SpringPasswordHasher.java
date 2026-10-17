package com.advancedlogin.adapter.out.security;

import com.advancedlogin.application.auth.port.out.PasswordHasher;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Delegating encoder: hashes are stored as "{bcrypt}$2a$10$...". The "{id}" prefix lets us move
 * to a stronger algorithm later while old hashes keep verifying.
 */
@Component
class SpringPasswordHasher implements PasswordHasher {

    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        return encoder.matches(rawPassword, passwordHash);
    }
}
