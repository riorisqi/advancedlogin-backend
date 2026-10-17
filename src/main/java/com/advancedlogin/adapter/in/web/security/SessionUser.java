package com.advancedlogin.adapter.in.web.security;

import java.io.Serial;
import java.io.Serializable;

/** Stored in the session (in Redis), so Serializable: Spring Session uses JDK serialization. */
public record SessionUser(String userId, String username) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
