package com.advancedlogin.adapter.in.web.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the real HTTP flow the browser will use (cookies + CSRF header) against a throwaway
 * Redis in Docker. The HttpClient's CookieManager plays the role of the browser's cookie jar.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthFlowIntegrationTest {

    @Container
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> redis = new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);

    @LocalServerPort
    int port;

    @Autowired
    StringRedisTemplate redisTemplate;

    private CookieManager cookies;
    private HttpClient http;

    @BeforeEach
    void newBrowser() {
        cookies = new CookieManager();
        http = HttpClient.newBuilder().cookieHandler(cookies).build();
    }

    @Test
    void anonymousUserIsRejectedFromMe() throws Exception {
        assertThat(get("/api/me").statusCode()).isEqualTo(401);
    }

    @Test
    void loginWithoutCsrfTokenIsForbidden() throws Exception {
        HttpResponse<String> res = post("/api/auth/login", aliceLogin("alice-dev-pass"), false);
        assertThat(res.statusCode()).isEqualTo(403);
    }

    @Test
    void wrongPasswordGivesProblemDetail() throws Exception {
        get("/api/auth/csrf");

        HttpResponse<String> res = post("/api/auth/login", aliceLogin("wrong"), true);

        assertThat(res.statusCode()).isEqualTo(401);
        assertThat(res.headers().firstValue("Content-Type")).hasValueSatisfying(
                ct -> assertThat(ct).startsWith("application/problem+json"));
        assertThat(cookie("SESSION")).isEmpty();
    }

    @Test
    void fullLoginMeLogoutFlow() throws Exception {
        get("/api/auth/csrf");
        assertThat(cookie("XSRF-TOKEN")).isPresent();

        HttpResponse<String> login = post("/api/auth/login", aliceLogin("alice-dev-pass"), true);
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(login.body()).contains("\"username\":\"alice\"", "\"displayName\":\"Alice\"");
        assertThat(cookie("SESSION")).isPresent();

        // The session really lives in Redis, under our namespace.
        assertThat(redisTemplate.keys("advancedlogin:session:*")).isNotEmpty();

        HttpResponse<String> me = get("/api/me");
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.body()).contains("\"username\":\"alice\"");

        // Login rotated the CSRF token, so fetch a fresh one before the next POST.
        get("/api/auth/csrf");
        assertThat(post("/api/auth/logout", "", true).statusCode()).isEqualTo(204);

        assertThat(get("/api/me").statusCode()).isEqualTo(401);
    }

    private static String aliceLogin(String password) {
        return "{\"username\":\"alice\",\"password\":\"" + password + "\"}";
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String json, boolean withCsrf)
            throws IOException, InterruptedException {
        HttpRequest.Builder req = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (withCsrf) {
            req.header("X-XSRF-TOKEN", cookie("XSRF-TOKEN").orElseThrow());
        }
        return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

    private Optional<String> cookie(String name) {
        return cookies.getCookieStore().get(uri("/")).stream()
                .filter(c -> c.getName().equals(name))
                .map(HttpCookie::getValue)
                .filter(v -> !v.isEmpty())
                .findFirst();
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
