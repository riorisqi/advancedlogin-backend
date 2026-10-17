package com.advancedlogin.application.qr;

import com.advancedlogin.domain.qr.BindingHash;
import com.advancedlogin.domain.qr.IllegalQrTransitionException;
import com.advancedlogin.domain.qr.QrSession;
import com.advancedlogin.domain.qr.QrSessionId;
import com.advancedlogin.domain.qr.QrStatus;
import com.advancedlogin.domain.qr.QrStatusUpdate;
import com.advancedlogin.domain.qr.QrTimeouts;
import com.advancedlogin.domain.qr.RequestingDevice;
import com.advancedlogin.domain.user.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QrSessionServiceTest {

    private static final Instant T0 = Instant.parse("2026-10-17T12:00:00Z");
    private static final QrTimeouts TIMEOUTS = new QrTimeouts(
            Duration.ofSeconds(90), Duration.ofSeconds(60), Duration.ofSeconds(30), Duration.ofSeconds(15));
    private static final QrSessionId ID = new QrSessionId("q".repeat(43));

    private final UserId alice = new UserId("alice-id");
    private final UserId bob = new UserId("bob-id");
    private final InMemoryQrSessionStore store = new InMemoryQrSessionStore();
    private final List<QrStatusUpdate> published = new ArrayList<>();
    private final QrSessionService service =
            new QrSessionService(store, published::add, Clock.fixed(T0, ZoneOffset.UTC), TIMEOUTS);

    @BeforeEach
    void createPendingSession() {
        store.create(QrSession.pending(ID, BindingHash.of("bind-secret"), new RequestingDevice("127.0.0.1", "ua"),
                T0.minusSeconds(10), TIMEOUTS));
    }

    @Test
    void statusRequiresTheBindingSecret() {
        assertThat(service.getStatus(ID, "bind-secret").status()).isEqualTo(QrStatus.PENDING);
        assertThatThrownBy(() -> service.getStatus(ID, "stolen-guess")).isInstanceOf(QrSessionNotFoundException.class);
        assertThatThrownBy(() -> service.getStatus(ID, null)).isInstanceOf(QrSessionNotFoundException.class);
    }

    @Test
    void scanThenApprovePersistsEachStep() {
        service.scan(ID, alice);
        assertThat(store.findById(ID).orElseThrow().status()).isEqualTo(QrStatus.SCANNED);

        service.approve(ID, alice);
        assertThat(store.findById(ID).orElseThrow().status()).isEqualTo(QrStatus.CONFIRMED);
    }

    @Test
    void unknownSessionIsNotFound() {
        assertThatThrownBy(() -> service.scan(new QrSessionId("z".repeat(43)), alice))
                .isInstanceOf(QrSessionNotFoundException.class);
    }

    @Test
    void expiredSessionIsNotFoundEvenIfStillStored() {
        QrSessionService later = new QrSessionService(store, published::add, Clock.fixed(T0.plusSeconds(120), ZoneOffset.UTC), TIMEOUTS);

        assertThatThrownBy(() -> later.scan(ID, alice)).isInstanceOf(QrSessionNotFoundException.class);
    }

    @Test
    void losingARaceIsReevaluatedAgainstTheWinnersState() {
        // Bob's scan lands between our read and our write: our CAS (expecting PENDING) fails,
        // the service re-reads, sees SCANNED-by-bob, and the domain says no.
        InMemoryQrSessionStore racingStore = new InMemoryQrSessionStore() {
            private boolean raced;

            @Override
            public synchronized boolean compareAndSet(QrStatus expected, QrSession updated) {
                if (!raced) {
                    raced = true;
                    QrSession current = findById(updated.id()).orElseThrow();
                    overwrite(current.scan(bob, T0, TIMEOUTS));
                }
                return super.compareAndSet(expected, updated);
            }
        };
        racingStore.create(store.findById(ID).orElseThrow());
        QrSessionService racingService = new QrSessionService(racingStore, published::add, Clock.fixed(T0, ZoneOffset.UTC), TIMEOUTS);

        assertThatThrownBy(() -> racingService.scan(ID, alice)).isInstanceOf(IllegalQrTransitionException.class);
        assertThat(racingStore.findById(ID).orElseThrow().scanner()).contains(bob);
        assertThat(published).as("the loser's change was never stored, so never announced").isEmpty();
    }

    @Test
    void publishesEachStoredTransitionButNotNoOps() {
        service.scan(ID, alice);
        service.scan(ID, alice); // idempotent re-scan: nothing changed
        service.approve(ID, alice);

        assertThat(published).extracting(QrStatusUpdate::status).containsExactly(QrStatus.SCANNED, QrStatus.CONFIRMED);
        assertThat(published.get(1).expiresAt()).isEqualTo(T0.plusSeconds(30));
    }
}
