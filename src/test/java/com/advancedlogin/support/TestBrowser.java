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

/** A minimal browser: a cookie jar, plus the XSRF-TOKEN → X-XSRF-TOKEN copying the SPA does on POSTs. */
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

    public HttpResponse<String> post(String path, String json) {
        return post(path, json, java.util.Map.of());
    }

    public HttpResponse<String> post(String path, String json, java.util.Map<String, String> headers) {
        if (cookie("XSRF-TOKEN").isEmpty()) {
            get("/api/auth/csrf");
        }
        HttpRequest.Builder request = jsonPost(path, json).header("X-XSRF-TOKEN", cookie("XSRF-TOKEN").orElseThrow());
        headers.forEach(request::header);
        return send(request);
    }

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

    /** Returns once the response headers arrived; {@code data:} lines are collected in the background. */
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

    public static final class SseStream implements AutoCloseable {

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
