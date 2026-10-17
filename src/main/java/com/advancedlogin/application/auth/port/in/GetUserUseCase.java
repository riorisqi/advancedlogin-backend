package com.advancedlogin.application.auth.port.in;

import com.advancedlogin.domain.user.User;
import com.advancedlogin.domain.user.UserId;

import java.util.Optional;

public interface GetUserUseCase {

    Optional<User> getUser(UserId id);
}
