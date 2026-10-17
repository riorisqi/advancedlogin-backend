package com.advancedlogin.application.auth;

import com.advancedlogin.application.auth.port.out.PasswordHasher;
import com.advancedlogin.application.auth.port.out.UserRepository;
import com.advancedlogin.application.ratelimit.RateLimitDecision;
import com.advancedlogin.application.ratelimit.TooManyRequestsException;
import com.advancedlogin.domain.user.User;
import com.advancedlogin.domain.user.UserId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticateUserServiceTest {

    private final User alice = new User(new UserId("u-1"), "alice", "Alice", "hashed:secret");
    private final RecordingHasher hasher = new RecordingHasher();
    private final UserRepository users = new UserRepository() {
        @Override
        public Optional<User> findByUsername(String username) {
            return "alice".equals(username) ? Optional.of(alice) : Optional.empty();
        }

        @Override
        public Optional<User> findById(UserId id) {
            return alice.id().equals(id) ? Optional.of(alice) : Optional.empty();
        }
    };
    private boolean overLimit = false;
    private final List<String> limitedSubjects = new ArrayList<>();
    private final AuthenticateUserService service = new AuthenticateUserService(users, hasher, (rule, subject) -> {
        limitedSubjects.add(rule + ":" + subject);
        return overLimit ? new RateLimitDecision(false, Duration.ofSeconds(30)) : RateLimitDecision.allow();
    });

    @Test
    void returnsUserWhenPasswordMatches() {
        assertThat(service.authenticate("alice", "secret")).isEqualTo(alice);
    }

    @Test
    void rejectsWrongPassword() {
        assertThatThrownBy(() -> service.authenticate("alice", "nope"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void rejectsUnknownUserWithSameExceptionAndStillChecksAHash() {
        hasher.checkedHashes.clear();

        assertThatThrownBy(() -> service.authenticate("mallory", "whatever"))
                .isInstanceOf(InvalidCredentialsException.class);
        // Timing defence: a hash comparison happens even though the user doesn't exist.
        assertThat(hasher.checkedHashes).hasSize(1);
    }

    @Test
    void perUsernameLimitStopsGuessingBeforeThePasswordIsChecked() {
        overLimit = true;
        hasher.checkedHashes.clear();

        assertThatThrownBy(() -> service.authenticate("  Alice ", "secret")).isInstanceOf(TooManyRequestsException.class);
        assertThat(hasher.checkedHashes).as("no password check while limited").isEmpty();
        assertThat(limitedSubjects).containsExactly("login-user:alice");
    }

    /** "Hash" is just a prefix; records what it was asked to compare. */
    private static final class RecordingHasher implements PasswordHasher {
        final List<String> checkedHashes = new ArrayList<>();

        @Override
        public String hash(String rawPassword) {
            return "hashed:" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String passwordHash) {
            checkedHashes.add(passwordHash);
            return passwordHash.equals(hash(rawPassword));
        }
    }
}
