package com.advancedlogin.application.auth;

import com.advancedlogin.application.auth.port.in.AuthenticateUserUseCase;
import com.advancedlogin.application.auth.port.out.PasswordHasher;
import com.advancedlogin.application.auth.port.out.UserRepository;
import com.advancedlogin.domain.user.User;

import java.util.Optional;

public class AuthenticateUserService implements AuthenticateUserUseCase {

    private final UserRepository users;
    private final PasswordHasher passwordHasher;

    /**
     * Hash of a throwaway value, checked when the username doesn't exist. Without it, "unknown
     * user" would return in ~0 ms while "wrong password" takes ~100 ms (BCrypt), letting an
     * attacker discover valid usernames by timing the response.
     */
    private final String dummyHash;

    public AuthenticateUserService(UserRepository users, PasswordHasher passwordHasher) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.dummyHash = passwordHasher.hash("timing-equalizer");
    }

    @Override
    public User authenticate(String username, String rawPassword) {
        Optional<User> user = users.findByUsername(username);
        String hash = user.map(User::passwordHash).orElse(dummyHash);
        boolean passwordMatches = passwordHasher.matches(rawPassword, hash);

        if (user.isEmpty() || !passwordMatches) {
            throw new InvalidCredentialsException();
        }
        return user.get();
    }
}
