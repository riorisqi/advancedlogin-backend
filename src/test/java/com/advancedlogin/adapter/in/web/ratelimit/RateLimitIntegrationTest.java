package com.advancedlogin.adapter.in.web.ratelimit;

import com.advancedlogin.support.RedisIntegrationTest;
import com.advancedlogin.support.TestBrowser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;

import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Rate limiting switched on, with tiny limits so a test can hit them quickly. */
@TestPropertySource(properties = {
        "app.rate-limit.enabled=true",
        "app.rate-limit.rules.qr-create.requests=3",
        "app.rate-limit.rules.login-ip.requests=100",
        "app.rate-limit.rules.login-user.requests=3",
})
class RateLimitIntegrationTest extends RedisIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    StringRedisTemplate redis;

    @BeforeEach
    void resetCounters() {
        var keys = redis.keys("rl:*");
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    @Test
    void qrCreationIsLimitedPerIp() {
        TestBrowser desktop = new TestBrowser(port);
        for (int i = 0; i < 3; i++) {
            assertThat(desktop.post("/api/qr/sessions", "").statusCode()).isEqualTo(201);
        }

        HttpResponse<String> limited = desktop.post("/api/qr/sessions", "");

        assertThat(limited.statusCode()).isEqualTo(429);
        assertThat(limited.headers().firstValue("Retry-After")).hasValueSatisfying(
                seconds -> assertThat(Integer.parseInt(seconds)).isBetween(1, 60));
        assertThat(limited.headers().firstValue("Content-Type")).hasValueSatisfying(
                ct -> assertThat(ct).startsWith("application/problem+json"));
        // Another client IP has its own budget.
        assertThat(new TestBrowser(port).post("/api/qr/sessions", "", Map.of("X-Forwarded-For", "192.168.1.77"))
                .statusCode()).isEqualTo(201);
    }

    @Test
    void passwordGuessingIsLimitedPerUsernameEvenWithTheRightPassword() {
        TestBrowser attacker = new TestBrowser(port);
        for (int i = 0; i < 3; i++) {
            assertThat(attacker.post("/api/auth/login", login("bob", "guess-" + i)).statusCode()).isEqualTo(401);
        }

        // Refused before the password is even checked.
        HttpResponse<String> limited = new TestBrowser(port).post("/api/auth/login", login("bob", "bob-dev-pass"));
        assertThat(limited.statusCode()).isEqualTo(429);
        assertThat(limited.headers().firstValue("Retry-After")).isPresent();
        assertThat(limited.body()).contains("Too many attempts");

        assertThat(new TestBrowser(port).post("/api/auth/login", login("alice", "alice-dev-pass")).statusCode())
                .isEqualTo(200);
    }

    @Test
    void countersLiveInRedisWithATtl() {
        new TestBrowser(port).post("/api/qr/sessions", "");

        assertThat(redis.keys("rl:qr-create:*")).hasSize(1).allSatisfy(key ->
                assertThat(redis.getExpire(key)).isBetween(1L, 60L));
    }

    private static String login(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }
}
