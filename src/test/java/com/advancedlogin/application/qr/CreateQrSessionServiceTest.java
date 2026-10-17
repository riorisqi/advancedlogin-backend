package com.advancedlogin.application.qr;

import com.advancedlogin.application.qr.port.in.CreateQrSessionUseCase.CreatedQrSession;
import com.advancedlogin.application.qr.port.out.QrSessionStore;
import com.advancedlogin.domain.qr.QrSession;
import com.advancedlogin.domain.qr.QrStatus;
import com.advancedlogin.domain.qr.RequestingDevice;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CreateQrSessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-17T12:00:00Z");
    private static final String ID_TOKEN = "a".repeat(43);
    private static final String SECRET_TOKEN = "b".repeat(43);

    private final List<QrSession> stored = new ArrayList<>();
    private final QrSessionStore store = stored::add;
    private final Iterator<String> tokens = List.of(ID_TOKEN, SECRET_TOKEN).iterator();

    private final CreateQrSessionService service = new CreateQrSessionService(
            store, tokens::next, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(90));

    @Test
    void createsPendingSessionThatExpiresAfterTtl() {
        RequestingDevice device = new RequestingDevice("203.0.113.7", "Chrome on Windows");

        CreatedQrSession created = service.create(device);

        QrSession session = created.session();
        assertThat(session.id().value()).isEqualTo(ID_TOKEN);
        assertThat(session.status()).isEqualTo(QrStatus.PENDING);
        assertThat(session.requestingDevice()).isEqualTo(device);
        assertThat(session.createdAt()).isEqualTo(NOW);
        assertThat(session.expiresAt()).isEqualTo(NOW.plusSeconds(90));
        assertThat(stored).containsExactly(session);
    }

    @Test
    void returnsRawSecretButStoresOnlyItsHash() {
        CreatedQrSession created = service.create(new RequestingDevice("127.0.0.1", null));

        assertThat(created.bindingSecret()).isEqualTo(SECRET_TOKEN);
        assertThat(created.session().bindingHash().value()).isNotEqualTo(SECRET_TOKEN);
        assertThat(created.session().bindingHash().matches(SECRET_TOKEN)).isTrue();
        assertThat(created.session().bindingHash().matches("c".repeat(43))).isFalse();
    }
}
