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

    /** Logs this browser in with a seeded test account (see app.seed-users). */
    public TestBrowser logIn(String username, String password) {
        HttpResponse<String> res = post("/api/auth/login",
                "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
        if (res.statusCode() != 200) {
            throw new IllegalStateException("login failed: " + res.statusCode() + " " + res.body());
        }
        return this;
    }

    public HttpResponse<String> postWithoutCsrf(String path, String json) {
        return send(jsonPost(path, json));
    }

    /**
     * Opens a Server-Sent Events stream like EventSource would, and collects the {@code data:}
     * lines in a queue on a background thread. Returns once the response headers have arrived.
     */
    public SseStream openEventStream(String path) {
        HttpRequest request = HttpRequest.newBuilder(uri(path)).header("Accept", "text/event-stream").GET().build();
        HttpResponse<java.util.stream.Stream<String>> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofLines());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        return new SseStream(response);
    }

    /** Minimal SSE reader: we only care about {@code data:} lines, and whether the stream ended. */
    public static final class SseStream implements AutoCloseable {

        /** Marker put in the queue when the server closes the stream. */
        public static final String END = "<end of stream>";

        private final HttpResponse<java.util.stream.Stream<String>> response;
        private final java.util.concurrent.BlockingQueue<String> data = new java.util.concurrent.LinkedBlockingQueue<>();

        SseStream(HttpResponse<java.util.stream.Stream<String>> response) {
            this.response = response;
            Thread.ofVirtual().start(() -> {
                try (var lines = response.body()) {
                    lines.filter(l -> l.startsWith("data:")).forEach(l -> data.add(l.substring(5).trim()));
                } catch (RuntimeException ignored) {
                    // connection closed by close()
                }
                data.add(END);
            });
        }

        public int statusCode() {
            return response.statusCode();
        }

        /** Next data payload, or fails the test after 5 seconds. */
        public String next() {
            try {
                String value = data.poll(5, java.util.concurrent.TimeUnit.SECONDS);
                if (value == null) {
                    throw new AssertionError("no SSE event within 5s");
                }
                return value;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void close() {
            response.body().close();
        }
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
