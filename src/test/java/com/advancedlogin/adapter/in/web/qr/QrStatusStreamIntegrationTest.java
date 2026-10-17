package com.advancedlogin.adapter.in.web.qr;

import com.advancedlogin.support.RedisIntegrationTest;
import com.advancedlogin.support.TestBrowser;
import com.advancedlogin.support.TestBrowser.SseStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** The desktop's live status stream: SSE over HTTP, fed by Redis Pub/Sub. */
class QrStatusStreamIntegrationTest extends RedisIntegrationTest {

    private static final Pattern QR_ID = Pattern.compile("\"qrId\":\"([A-Za-z0-9_-]{43})\"");

    @LocalServerPort
    int port;

    @Autowired
    StringRedisTemplate redis;

    private TestBrowser desktop;
    private TestBrowser alicePhone;
    private String qrId;

    @BeforeEach
    void setUp() {
        desktop = new TestBrowser(port);
        alicePhone = new TestBrowser(port).logIn("alice", "alice-dev-pass");
        qrId = qrId(desktop.post("/api/qr/sessions", ""));
    }

    @Test
    void streamStartsWithSnapshotThenPushesEachChange() {
        try (SseStream stream = desktop.openEventStream(eventsPath())) {
            assertThat(stream.statusCode()).isEqualTo(200);
            assertThat(stream.next()).contains("\"status\":\"PENDING\"", "\"serverTime\":");

            alicePhone.post(qrPath("/scan"), "");
            assertThat(stream.next()).contains("\"status\":\"SCANNED\"");

            alicePhone.post(qrPath("/approve"), "");
            assertThat(stream.next()).contains("\"status\":\"CONFIRMED\"");
        }
    }

    @Test
    void finalStatusClosesTheStream() {
        try (SseStream stream = desktop.openEventStream(eventsPath())) {
            stream.next(); // PENDING
            alicePhone.post(qrPath("/scan"), "");
            stream.next(); // SCANNED

            alicePhone.post(qrPath("/reject"), "");

            assertThat(stream.next()).contains("\"status\":\"REJECTED\"");
            assertThat(stream.next()).isEqualTo(SseStream.END);
        }
    }

    @Test
    void lateJoinerGetsCurrentStateImmediately() {
        alicePhone.post(qrPath("/scan"), "");

        // e.g. EventSource reconnecting after a network blip: no history needed, just the snapshot.
        try (SseStream stream = desktop.openEventStream(eventsPath())) {
            assertThat(stream.next()).contains("\"status\":\"SCANNED\"");
        }
    }

    @Test
    void updatesPublishedByAnotherInstanceReachThisOnesStreams() {
        try (SseStream stream = desktop.openEventStream(eventsPath())) {
            stream.next(); // PENDING

            // Pretend backend instance B handled the phone's scan and published to Redis.
            redis.convertAndSend("qr:events:" + qrId, "SCANNED:" + (System.currentTimeMillis() + 60_000));

            assertThat(stream.next()).contains("\"status\":\"SCANNED\"");
        }
    }

    @Test
    void streamRequiresTheBindingCookie() {
        try (SseStream stream = new TestBrowser(port).openEventStream(eventsPath())) {
            assertThat(stream.statusCode()).isEqualTo(404);
        }
    }

    private String eventsPath() {
        return "/api/qr/sessions/" + qrId + "/events";
    }

    private String qrPath(String action) {
        return "/api/qr/sessions/" + qrId + action;
    }

    private static String qrId(HttpResponse<String> res) {
        Matcher m = QR_ID.matcher(res.body());
        assertThat(m.find()).as("qrId in %s", res.body()).isTrue();
        return m.group(1);
    }
}
