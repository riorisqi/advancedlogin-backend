package com.advancedlogin.application.auth.port.out;

/** Hashing is infrastructure (BCrypt today, Argon2 tomorrow); the use case only needs these two operations. */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String passwordHash);
}
