package com.advancedlogin.adapter.in.web.qr;

import com.advancedlogin.support.RedisIntegrationTest;
import com.advancedlogin.support.TestBrowser;
import com.advancedlogin.support.TestBrowser.SseStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** The whole story: desktop shows QR → phone scans + approves → desktop is logged in. */
class QrLoginEndToEndIntegrationTest extends RedisIntegrationTest {

    private static final Pattern QR_ID = Pattern.compile("\"qrId\":\"([A-Za-z0-9_-]{43})\"");
    private static final Pattern CODE = Pattern.compile("\"exchangeCode\":\"([A-Za-z0-9_-]{43})\"");

    @LocalServerPort
    int port;

    private TestBrowser desktop;
    private TestBrowser alicePhone;
    private String qrId;

    @BeforeEach
    void setUp() {
        desktop = new TestBrowser(port);
        alicePhone = new TestBrowser(port).logIn("alice", "alice-dev-pass");
        qrId = match(QR_ID, desktop.post("/api/qr/sessions", "").body());
    }

    @Test
    void desktopEndsUpLoggedInAsThePhoneUser() {
        assertThat(desktop.get("/api/me").statusCode()).isEqualTo(401);

        String code;
        try (SseStream stream = desktop.openEventStream(path("/events"))) {
            stream.next(); // PENDING
            alicePhone.post(path("/scan"), "");
            stream.next(); // SCANNED
            alicePhone.post(path("/approve"), "");

            String confirmed = stream.next();
            assertThat(confirmed).contains("\"status\":\"CONFIRMED\"");
            code = match(CODE, confirmed);

            HttpResponse<String> exchange = desktop.post(path("/exchange"), "{\"code\":\"" + code + "\"}");
            assertThat(exchange.statusCode()).isEqualTo(200);
            assertThat(exchange.body()).contains("\"username\":\"alice\"");

            assertThat(stream.next()).contains("\"status\":\"CONSUMED\"");
            assertThat(stream.next()).isEqualTo(SseStream.END);
        }

        // A real session now, exactly like after a password login.
        assertThat(desktop.cookie("SESSION")).isPresent();
        assertThat(desktop.get("/api/me").body()).contains("\"username\":\"alice\"");
        // The binding cookie is cleared, so the same browser can't redeem again either.
        assertThat(desktop.cookie("qr_bind")).isEmpty();
        assertThat(desktop.post(path("/exchange"), "{\"code\":\"" + code + "\"}").statusCode()).isEqualTo(404);
    }

    @Test
    void codeIsAlsoAvailableFromThePollingSnapshot() {
        approveByAlice();

        HttpResponse<String> snapshot = desktop.get(path(""));

        assertThat(snapshot.body()).contains("\"status\":\"CONFIRMED\"");
        assertThat(match(CODE, snapshot.body())).hasSize(43);
    }

    @Test
    void stolenCodeIsUselessWithoutTheBindingCookie() {
        approveByAlice();
        String code = match(CODE, desktop.get(path("")).body());

        // An attacker who somehow saw the code (e.g. shoulder-surfed a log) but not the cookie.
        TestBrowser attacker = new TestBrowser(port);
        assertThat(attacker.post(path("/exchange"), "{\"code\":\"" + code + "\"}").statusCode()).isEqualTo(404);
        assertThat(attacker.get("/api/me").statusCode()).isEqualTo(401);

        // ...and the real desktop can still finish.
        assertThat(desktop.post(path("/exchange"), "{\"code\":\"" + code + "\"}").statusCode()).isEqualTo(200);
    }

    @Test
    void wrongCodeOrNoApprovalIsRefused() {
        assertThat(desktop.post(path("/exchange"), "{\"code\":\"" + "x".repeat(43) + "\"}").statusCode())
                .as("not approved yet").isEqualTo(409);

        approveByAlice();

        assertThat(desktop.post(path("/exchange"), "{\"code\":\"" + "x".repeat(43) + "\"}").statusCode())
                .as("wrong code").isEqualTo(400);
        assertThat(desktop.post(path("/exchange"), "{}").statusCode()).as("missing code").isEqualTo(400);
        assertThat(desktop.get("/api/me").statusCode()).isEqualTo(401);
    }

    private void approveByAlice() {
        alicePhone.post(path("/scan"), "");
        alicePhone.post(path("/approve"), "");
    }

    private String path(String suffix) {
        return "/api/qr/sessions/" + qrId + suffix;
    }

    private static String match(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        assertThat(m.find()).as("%s in %s", pattern, text).isTrue();
        return m.group(1);
    }
}
