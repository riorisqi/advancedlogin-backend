package com.advancedlogin.application.auth;

import com.advancedlogin.application.auth.port.in.GetUserUseCase;
import com.advancedlogin.application.auth.port.out.UserRepository;
import com.advancedlogin.domain.user.User;
import com.advancedlogin.domain.user.UserId;

import java.util.Optional;

public class GetUserService implements GetUserUseCase {

    private final UserRepository users;

    public GetUserService(UserRepository users) {
        this.users = users;
    }

    @Override
    public Optional<User> getUser(UserId id) {
        return users.findById(id);
    }
}
