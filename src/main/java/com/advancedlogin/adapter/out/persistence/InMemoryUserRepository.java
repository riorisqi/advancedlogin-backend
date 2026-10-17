package com.advancedlogin.adapter.out.persistence;

import com.advancedlogin.application.auth.port.out.UserRepository;
import com.advancedlogin.domain.user.User;
import com.advancedlogin.domain.user.UserId;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Users live in memory for now (seeded at startup by {@code config.DevUserSeeder}). Swapping in
 * Postgres later means writing another {@link UserRepository} adapter; no use case changes.
 */
@Component
public class InMemoryUserRepository implements UserRepository {

    private final Map<UserId, User> byId = new ConcurrentHashMap<>();
    private final Map<String, User> byUsername = new ConcurrentHashMap<>();

    public void add(User user) {
        byId.put(user.id(), user);
        byUsername.put(normalize(user.username()), user);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(byUsername.get(normalize(username)));
    }

    @Override
    public Optional<User> findById(UserId id) {
        return Optional.ofNullable(byId.get(id));
    }

    private static String normalize(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
