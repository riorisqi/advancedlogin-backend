package com.advancedlogin.application.auth;

import com.advancedlogin.application.auth.port.in.AuthenticateUserUseCase;
import com.advancedlogin.application.auth.port.out.PasswordHasher;
import com.advancedlogin.application.auth.port.out.UserRepository;
import com.advancedlogin.application.ratelimit.RateLimitDecision;
import com.advancedlogin.application.ratelimit.TooManyRequestsException;
import com.advancedlogin.application.ratelimit.port.in.CheckRateLimitUseCase;
import com.advancedlogin.domain.user.User;

import java.util.Locale;
import java.util.Optional;

public class AuthenticateUserService implements AuthenticateUserUseCase {

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final CheckRateLimitUseCase rateLimits;

    // Checked for unknown usernames too, so response time doesn't reveal which accounts exist.
    private final String dummyHash;

    public AuthenticateUserService(UserRepository users, PasswordHasher passwordHasher,
                                   CheckRateLimitUseCase rateLimits) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.rateLimits = rateLimits;
        this.dummyHash = passwordHasher.hash("timing-equalizer");
    }

    @Override
    public User authenticate(String username, String rawPassword) {
        // Per account, on top of the per-IP filter: rotating IPs doesn't buy more guesses. Unknown
        // usernames count too, so the limit doesn't reveal which accounts exist.
        RateLimitDecision decision = rateLimits.check("login-user", username.trim().toLowerCase(Locale.ROOT));
        if (!decision.allowed()) {
            throw new TooManyRequestsException(decision.retryAfter());
        }

        Optional<User> user = users.findByUsername(username);
        String hash = user.map(User::passwordHash).orElse(dummyHash);
        boolean passwordMatches = passwordHasher.matches(rawPassword, hash);

        if (user.isEmpty() || !passwordMatches) {
            throw new InvalidCredentialsException();
        }
        return user.get();
    }
}
