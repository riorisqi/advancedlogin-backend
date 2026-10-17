package com.advancedlogin.adapter.in.web.qr;

import com.advancedlogin.support.RedisIntegrationTest;
import com.advancedlogin.support.TestBrowser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Three browsers: the desktop, Alice's phone, and Bob's phone (another user who got hold of the QR). */
class QrApprovalFlowIntegrationTest extends RedisIntegrationTest {

    private static final Pattern QR_ID = Pattern.compile("\"qrId\":\"([A-Za-z0-9_-]{43})\"");

    @LocalServerPort
    int port;

    private TestBrowser desktop;
    private TestBrowser alicePhone;
    private TestBrowser bobPhone;
    private String qrId;

    @BeforeEach
    void setUp() {
        desktop = new TestBrowser(port);
        alicePhone = new TestBrowser(port).logIn("alice", "alice-dev-pass");
        bobPhone = new TestBrowser(port).logIn("bob", "bob-dev-pass");
        qrId = qrId(desktop.post("/api/qr/sessions", ""));
    }

    @Test
    void happyPathScanThenApprove() {
        assertThat(desktopStatus()).contains("\"status\":\"PENDING\"");

        HttpResponse<String> scan = alicePhone.post(qrPath("/scan"), "");
        assertThat(scan.statusCode()).isEqualTo(200);
        assertThat(scan.body()).contains("\"qrId\":\"" + qrId + "\"", "\"ipAddress\":", "\"browser\":", "\"os\":",
                "\"approximateLocation\":\"Local network\"");
        assertThat(desktopStatus()).contains("\"status\":\"SCANNED\"");

        assertThat(alicePhone.post(qrPath("/approve"), "").statusCode()).isEqualTo(204);
        assertThat(desktopStatus()).contains("\"status\":\"CONFIRMED\"");
    }

    @Test
    void rejectIsVisibleToTheDesktop() {
        alicePhone.post(qrPath("/scan"), "");

        assertThat(alicePhone.post(qrPath("/reject"), "").statusCode()).isEqualTo(204);
        assertThat(desktopStatus()).contains("\"status\":\"REJECTED\"");
        assertThat(alicePhone.post(qrPath("/approve"), "").statusCode()).isEqualTo(409);
    }

    @Test
    void onceScannedOtherUsersAreLockedOut() {
        alicePhone.post(qrPath("/scan"), "");

        assertThat(bobPhone.post(qrPath("/scan"), "").statusCode()).isEqualTo(409);
        assertThat(bobPhone.post(qrPath("/approve"), "").statusCode()).isEqualTo(409);
        assertThat(alicePhone.post(qrPath("/scan"), "").statusCode()).as("idempotent re-scan").isEqualTo(200);
    }

    @Test
    void knowingTheQrIdIsNotEnoughToWatchIt() {
        // Someone photographed the screen: same qrId, no qr_bind cookie.
        TestBrowser onlooker = new TestBrowser(port);

        assertThat(onlooker.get("/api/qr/sessions/" + qrId).statusCode()).isEqualTo(404);
        assertThat(desktop.get("/api/qr/sessions/" + qrId).statusCode()).isEqualTo(200);
    }

    @Test
    void phoneEndpointsNeedALoggedInUser() {
        assertThat(new TestBrowser(port).post(qrPath("/scan"), "").statusCode()).isEqualTo(401);
    }

    @Test
    void unknownOrMalformedIdsAreNotFound() {
        assertThat(alicePhone.post("/api/qr/sessions/" + "x".repeat(43) + "/scan", "").statusCode()).isEqualTo(404);
        assertThat(alicePhone.post("/api/qr/sessions/not-a-qr-id/scan", "").statusCode()).isEqualTo(404);
    }

    @Test
    void whenTwoPhonesScanAtTheSameInstantExactlyOneWins() throws Exception {
        CountDownLatch startTogether = new CountDownLatch(1);
        Callable<Integer> aliceScan = () -> { startTogether.await(); return alicePhone.post(qrPath("/scan"), "").statusCode(); };
        Callable<Integer> bobScan = () -> { startTogether.await(); return bobPhone.post(qrPath("/scan"), "").statusCode(); };

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<Integer>> results = List.of(pool.submit(aliceScan), pool.submit(bobScan));
            startTogether.countDown();

            List<Integer> statuses = List.of(results.get(0).get(), results.get(1).get());
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        }
    }

    private String desktopStatus() {
        HttpResponse<String> res = desktop.get("/api/qr/sessions/" + qrId);
        assertThat(res.statusCode()).isEqualTo(200);
        return res.body();
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
