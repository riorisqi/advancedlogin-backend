package com.advancedlogin.application.auth.port.out;

import com.advancedlogin.domain.user.User;
import com.advancedlogin.domain.user.UserId;

import java.util.Optional;

public interface UserRepository {

    Optional<User> findByUsername(String username);

    Optional<User> findById(UserId id);
}
