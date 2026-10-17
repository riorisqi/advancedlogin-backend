package com.advancedlogin.domain.user;

import java.util.Objects;

/** {@code passwordHash} is already hashed: the domain never sees a raw password. */
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
