package com.advancedlogin.adapter.common.redis;

import com.advancedlogin.domain.qr.QrSessionId;
import com.advancedlogin.domain.qr.QrStatus;
import com.advancedlogin.domain.qr.QrStatusUpdate;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class QrEventChannelTest {

    private final QrSessionId id = new QrSessionId("q".repeat(43));

    @Test
    void roundTrips() {
        QrStatusUpdate update = new QrStatusUpdate(id, QrStatus.SCANNED, Instant.ofEpochMilli(1_792_223_319_015L));

        String channel = QrEventChannel.channel(id);
        String payload = QrEventChannel.encode(update);

        assertThat(channel).isEqualTo("qr:events:" + id.value());
        assertThat(payload).isEqualTo("SCANNED:1792223319015");
        assertThat(QrEventChannel.decode(channel, payload)).contains(update);
    }

    @Test
    void dropsMalformedMessages() {
        assertThat(QrEventChannel.decode("qr:events:short", "SCANNED:1")).isEmpty();
        assertThat(QrEventChannel.decode(QrEventChannel.channel(id), "NOPE:1")).isEmpty();
        assertThat(QrEventChannel.decode(QrEventChannel.channel(id), "SCANNED")).isEmpty();
        assertThat(QrEventChannel.decode(QrEventChannel.channel(id), "SCANNED:abc")).isEmpty();
    }
}
