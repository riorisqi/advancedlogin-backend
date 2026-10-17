package com.advancedlogin.domain.user;

import java.util.Objects;

/**
 * A registered user. {@code passwordHash} is an opaque, already-hashed string: the domain never
 * sees or stores a raw password, and doesn't care which algorithm produced the hash.
 */
public record User(UserId id, String username, String displayName, String passwordHash) {

    public User {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(passwordHash, "passwordHash");
    }

    @Override
    public String toString() {
        // Never leak the hash into logs.
        return "User[id=%s, username=%s]".formatted(id.value(), username);
    }
}
