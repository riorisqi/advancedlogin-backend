package com.advancedlogin.adapter.in.web.auth;

import com.advancedlogin.support.RedisIntegrationTest;
import com.advancedlogin.support.TestBrowser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.net.http.HttpResponse;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Drives the real HTTP flow the browser will use (cookies + CSRF header). */
class AuthFlowIntegrationTest extends RedisIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    StringRedisTemplate redisTemplate;

    private TestBrowser browser;

    @BeforeEach
    void newBrowser() {
        browser = new TestBrowser(port);
    }

    @Test
    void anonymousUserIsRejectedFromMe() {
        assertThat(browser.get("/api/me").statusCode()).isEqualTo(401);
    }

    @Test
    void loginWithoutCsrfTokenIsForbidden() {
        HttpResponse<String> res = browser.postWithoutCsrf("/api/auth/login", aliceLogin("alice-dev-pass"));
        assertThat(res.statusCode()).isEqualTo(403);
    }

    @Test
    void wrongPasswordGivesProblemDetail() {
        HttpResponse<String> res = browser.post("/api/auth/login", aliceLogin("wrong"));

        assertThat(res.statusCode()).isEqualTo(401);
        assertThat(res.headers().firstValue("Content-Type")).hasValueSatisfying(
                ct -> assertThat(ct).startsWith("application/problem+json"));
        assertThat(browser.cookie("SESSION")).isEmpty();
    }

    @Test
    void fullLoginMeLogoutFlow() {
        browser.get("/api/auth/csrf");
        String csrfBeforeLogin = browser.cookie("XSRF-TOKEN").orElseThrow();

        HttpResponse<String> login = browser.post("/api/auth/login", aliceLogin("alice-dev-pass"));
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(login.body()).contains("\"username\":\"alice\"", "\"displayName\":\"Alice\"");
        assertThat(browser.cookie("SESSION")).isPresent();

        // The session really lives in Redis, under our namespace.
        assertThat(redisTemplate.keys("advancedlogin:session:*")).isNotEmpty();

        HttpResponse<String> me = browser.get("/api/me");
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.body()).contains("\"username\":\"alice\"");

        // Login rotated the CSRF token: the pre-login value is no longer the one in the jar.
        // (post() fetches a fresh one if the cookie is gone, like the SPA.)
        assertThat(browser.cookie("XSRF-TOKEN")).isNotEqualTo(Optional.of(csrfBeforeLogin));
        assertThat(browser.post("/api/auth/logout", "").statusCode()).isEqualTo(204);

        assertThat(browser.get("/api/me").statusCode()).isEqualTo(401);
    }

    private static String aliceLogin(String password) {
        return "{\"username\":\"alice\",\"password\":\"" + password + "\"}";
    }
}
