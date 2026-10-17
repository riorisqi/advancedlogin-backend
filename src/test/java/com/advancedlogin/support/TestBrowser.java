package com.advancedlogin.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

/**
 * A minimal "browser" for integration tests: keeps a cookie jar and, like the SPA's httpClient,
 * copies the XSRF-TOKEN cookie into the X-XSRF-TOKEN header on POSTs.
 */
public final class TestBrowser {

    private final int port;
    private final CookieManager cookies = new CookieManager();
    private final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).build();

    public TestBrowser(int port) {
        this.port = port;
    }

    public HttpResponse<String> get(String path) {
        return send(HttpRequest.newBuilder(uri(path)).GET());
    }

    /** POST with the CSRF header, fetching a token first if the cookie is missing (as the SPA does). */
    public HttpResponse<String> post(String path, String json) {
        if (cookie("XSRF-TOKEN").isEmpty()) {
            get("/api/auth/csrf");
        }
        return send(jsonPost(path, json).header("X-XSRF-TOKEN", cookie("XSRF-TOKEN").orElseThrow()));
    }

    public HttpResponse<String> postWithoutCsrf(String path, String json) {
        return send(jsonPost(path, json));
    }

    public Optional<String> cookie(String name) {
        return cookies.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals(name))
                .map(HttpCookie::getValue)
                .filter(v -> !v.isEmpty())
                .findFirst();
    }

    private HttpRequest.Builder jsonPost(String path, String json) {
        return HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
    }

    private HttpResponse<String> send(HttpRequest.Builder request) {
        try {
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
