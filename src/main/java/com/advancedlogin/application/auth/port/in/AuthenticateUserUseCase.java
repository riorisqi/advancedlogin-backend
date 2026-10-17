package com.advancedlogin.application.auth.port.in;

import com.advancedlogin.application.auth.InvalidCredentialsException;
import com.advancedlogin.domain.user.User;

public interface AuthenticateUserUseCase {

    /**
     * Verifies a username/password pair.
     *
     * @throws InvalidCredentialsException if the user doesn't exist or the password is wrong
     *         (deliberately the same exception for both, so callers can't tell which)
     */
    User authenticate(String username, String rawPassword);
}
