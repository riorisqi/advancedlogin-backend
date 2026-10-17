package com.advancedlogin.domain.qr;

import com.advancedlogin.domain.user.UserId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The state machine, tested as plain Java: no Spring, no Redis, runs in milliseconds. */
class QrSessionTest {

    static final QrTimeouts TIMEOUTS = new QrTimeouts(
            Duration.ofSeconds(90), Duration.ofSeconds(60), Duration.ofSeconds(30), Duration.ofSeconds(15));
    static final Instant T0 = Instant.parse("2026-10-17T12:00:00Z");

    private final UserId alice = new UserId("alice-id");
    private final UserId bob = new UserId("bob-id");
    private final QrSession pending = QrSession.pending(new QrSessionId("q".repeat(43)), BindingHash.of("secret"),
            new RequestingDevice("127.0.0.1", "ua"), T0, TIMEOUTS);

    @Test
    void scanLocksSessionToUserAndRestartsTheClock() {
        Instant scanTime = T0.plusSeconds(80);

        QrSession scanned = pending.scan(alice, scanTime, TIMEOUTS);

        assertThat(scanned.status()).isEqualTo(QrStatus.SCANNED);
        assertThat(scanned.scanner()).contains(alice);
        assertThat(scanned.scannedAt()).isEqualTo(scanTime);
        assertThat(scanned.expiresAt()).isEqualTo(scanTime.plusSeconds(60)); // not T0 + 90s
    }

    @Test
    void sameUserScanningAgainIsANoOp() {
        QrSession scanned = pending.scan(alice, T0, TIMEOUTS);

        assertThat(scanned.scan(alice, T0.plusSeconds(5), TIMEOUTS)).isSameAs(scanned);
    }

    @Test
    void anotherUserCannotScanAnAlreadyScannedSession() {
        QrSession scanned = pending.scan(alice, T0, TIMEOUTS);

        assertThatThrownBy(() -> scanned.scan(bob, T0, TIMEOUTS)).isInstanceOf(IllegalQrTransitionException.class);
    }

    @Test
    void onlyTheScannerCanApproveOrReject() {
        QrSession scanned = pending.scan(alice, T0, TIMEOUTS);

        assertThatThrownBy(() -> scanned.approve(bob, T0, TIMEOUTS)).isInstanceOf(IllegalQrTransitionException.class);
        assertThatThrownBy(() -> scanned.reject(bob, T0, TIMEOUTS)).isInstanceOf(IllegalQrTransitionException.class);

        assertThat(scanned.approve(alice, T0, TIMEOUTS).status()).isEqualTo(QrStatus.CONFIRMED);
        assertThat(scanned.reject(alice, T0, TIMEOUTS).status()).isEqualTo(QrStatus.REJECTED);
    }

    @Test
    void cannotApproveOrRejectBeforeScanning() {
        assertThatThrownBy(() -> pending.approve(alice, T0, TIMEOUTS)).isInstanceOf(IllegalQrTransitionException.class);
        assertThatThrownBy(() -> pending.reject(alice, T0, TIMEOUTS)).isInstanceOf(IllegalQrTransitionException.class);
    }

    @Test
    void terminalStatesAcceptNoFurtherMoves() {
        QrSession confirmed = pending.scan(alice, T0, TIMEOUTS).approve(alice, T0, TIMEOUTS);
        QrSession rejected = pending.scan(alice, T0, TIMEOUTS).reject(alice, T0, TIMEOUTS);

        assertThatThrownBy(() -> confirmed.reject(alice, T0, TIMEOUTS)).isInstanceOf(IllegalQrTransitionException.class);
        assertThatThrownBy(() -> rejected.approve(alice, T0, TIMEOUTS)).isInstanceOf(IllegalQrTransitionException.class);
        assertThatThrownBy(() -> rejected.scan(bob, T0, TIMEOUTS)).isInstanceOf(IllegalQrTransitionException.class);
    }
}
