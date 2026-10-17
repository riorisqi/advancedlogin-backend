package com.advancedlogin.adapter.in.web.security;

import java.io.Serial;
import java.io.Serializable;

/**
 * What we keep in the HTTP session (and therefore in Redis) for a logged-in user: just enough to
 * identify them. Profile data is looked up fresh via the use case when needed.
 *
 * <p>Serializable because Spring Session stores the SecurityContext with JDK serialization.
 */
public record SessionUser(String userId, String username) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
