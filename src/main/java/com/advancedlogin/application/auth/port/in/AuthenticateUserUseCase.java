package com.advancedlogin.application.auth.port.in;

import com.advancedlogin.application.auth.InvalidCredentialsException;
import com.advancedlogin.domain.user.User;

public interface AuthenticateUserUseCase {

    /** @throws InvalidCredentialsException for an unknown user or a wrong password, deliberately alike */
    User authenticate(String username, String rawPassword);
}
