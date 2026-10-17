package com.advancedlogin.domain.user;

import java.util.Objects;
import java.util.UUID;

/** Typed id: a method taking a {@code UserId} can't be handed a username by mistake. */
public record UserId(String value) {

    public UserId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("UserId must not be blank");
        }
    }

    public static UserId random() {
        return new UserId(UUID.randomUUID().toString());
    }
}
