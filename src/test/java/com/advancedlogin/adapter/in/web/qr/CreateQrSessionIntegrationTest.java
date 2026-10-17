package com.advancedlogin.adapter.in.web.qr;

import com.advancedlogin.domain.qr.BindingHash;
import com.advancedlogin.support.RedisIntegrationTest;
import com.advancedlogin.support.TestBrowser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class CreateQrSessionIntegrationTest extends RedisIntegrationTest {

    private static final Pattern QR_ID = Pattern.compile("\"qrId\":\"([A-Za-z0-9_-]{43})\"");

    @LocalServerPort
    int port;

    @Autowired
    StringRedisTemplate redis;

    @Test
    void anonymousBrowserCreatesPendingSessionBoundToItsCookie() {
        TestBrowser desktop = new TestBrowser(port);

        HttpResponse<String> res = desktop.post("/api/qr/sessions", "");

        assertThat(res.statusCode()).isEqualTo(201);
        String qrId = qrId(res);
        assertThat(res.body()).contains("\"qrContent\":\"http://localhost:5173/m/approve/" + qrId + "\"");
        assertThat(res.headers().firstValue("Location")).hasValue("/api/qr/sessions/" + qrId);

        String setCookie = res.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith("qr_bind=")).findFirst().orElseThrow();
        assertThat(setCookie).contains("HttpOnly", "SameSite=Strict", "Path=/api/qr");
        String bindingSecret = desktop.cookie("qr_bind").orElseThrow();

        Map<Object, Object> stored = redis.opsForHash().entries("qr:s:" + qrId);
        assertThat(stored).containsEntry("status", "PENDING");
        assertThat(stored.get("bindHash")).isEqualTo(BindingHash.of(bindingSecret).value()).isNotEqualTo(bindingSecret);
        assertThat(stored.get("bindHash").toString()).doesNotContain(bindingSecret);
        assertThat(redis.getExpire("qr:s:" + qrId, TimeUnit.SECONDS)).isBetween(1L, 90L);

        // Page JS must never see the secret.
        assertThat(res.body()).doesNotContain(bindingSecret);
    }

    @Test
    void eachRequestGetsAFreshUnrelatedId() {
        TestBrowser desktop = new TestBrowser(port);

        String first = qrId(desktop.post("/api/qr/sessions", ""));
        String second = qrId(desktop.post("/api/qr/sessions", ""));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void realClientIpAndOriginComeThroughTheDevProxy() {
        // What the Vite dev proxy adds for a desktop on the LAN.
        HttpResponse<String> res = new TestBrowser(port).post("/api/qr/sessions", "", Map.of(
                "X-Forwarded-For", "192.168.1.23",
                "Origin", "https://192.168.1.5:5173"));

        String qrId = qrId(res);
        assertThat(res.body()).contains("\"qrContent\":\"https://192.168.1.5:5173/m/approve/" + qrId + "\"");
        // Trusted: the request came from 127.0.0.1, an internal proxy for RemoteIpValve.
        assertThat(redis.opsForHash().get("qr:s:" + qrId, "ip")).isEqualTo("192.168.1.23");
    }

    @Test
    void creatingRequiresCsrfToken() {
        assertThat(new TestBrowser(port).postWithoutCsrf("/api/qr/sessions", "").statusCode()).isEqualTo(403);
    }

    private static String qrId(HttpResponse<String> res) {
        Matcher m = QR_ID.matcher(res.body());
        assertThat(m.find()).as("qrId in %s", res.body()).isTrue();
        return m.group(1);
    }
}
