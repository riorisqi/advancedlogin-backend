package com.advancedlogin.config;

import com.advancedlogin.adapter.out.persistence.InMemoryUserRepository;
import com.advancedlogin.application.auth.port.out.PasswordHasher;
import com.advancedlogin.domain.user.User;
import com.advancedlogin.domain.user.UserId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** Seeds the test accounts from {@code app.seed-users} (there's no sign-up flow). */
@Configuration
@EnableConfigurationProperties(DevUserSeeder.SeedUsersProperties.class)
class DevUserSeeder {

    DevUserSeeder(SeedUsersProperties properties, InMemoryUserRepository users, PasswordHasher passwordHasher) {
        for (SeedUser seed : properties.seedUsers()) {
            users.add(new User(UserId.random(), seed.username(), seed.displayName(),
                    passwordHasher.hash(seed.password())));
        }
    }

    @ConfigurationProperties(prefix = "app")
    record SeedUsersProperties(List<SeedUser> seedUsers) {

        SeedUsersProperties {
            seedUsers = seedUsers == null ? List.of() : List.copyOf(seedUsers);
        }
    }

    record SeedUser(String username, String displayName, String password) {
    }
}
