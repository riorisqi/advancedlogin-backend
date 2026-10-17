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

    /**
     * Hash of a throwaway value, checked when the username doesn't exist. Without it, "unknown
     * user" would return in ~0 ms while "wrong password" takes ~100 ms (BCrypt), letting an
     * attacker discover valid usernames by timing the response.
     */
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
        // Per *account*, on top of the per-IP limit in the HTTP filter: an attacker rotating
        // through many IPs still only gets a few guesses per minute at one username. Counted for
        // unknown usernames too, so the limit itself doesn't reveal which accounts exist.
        // (Trade-off: someone can lock a known username out for a minute. Mild, and temporary.)
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
