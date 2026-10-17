package com.advancedlogin.adapter.in.web.qr;

import com.advancedlogin.domain.qr.QrSessionId;
import com.advancedlogin.domain.qr.QrStatusUpdate;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Open SSE connections on <em>this</em> instance. A stream lives in one JVM, so updates arrive via
 * Redis Pub/Sub ({@link #deliver}) rather than straight from the use case.
 */
@Component
public class QrStatusStreams {

    // Longer than any session lives; if it runs out, EventSource simply reconnects.
    static final Duration STREAM_TIMEOUT = Duration.ofMinutes(4);

    private final Map<QrSessionId, Set<SseEmitter>> streams = new ConcurrentHashMap<>();
    private final Clock clock;

    QrStatusStreams(Clock clock) {
        this.clock = clock;
    }

    SseEmitter open(QrSessionId id) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT.toMillis());
        streams.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet()).add(emitter);
        Runnable remove = () -> remove(id, emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        return emitter;
    }

    public void deliver(QrStatusUpdate update) {
        Set<SseEmitter> emitters = streams.get(update.id());
        if (emitters == null) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            send(emitter, update);
        }
    }

    void send(SseEmitter emitter, QrStatusUpdate update) {
        try {
            emitter.send(SseEmitter.event()
                    .name("status")
                    .data(QrStatusEvent.of(update, clock), MediaType.APPLICATION_JSON));
            if (update.isFinal()) {
                emitter.complete();
            }
        } catch (IOException | IllegalStateException e) {
            // Client went away.
            emitter.completeWithError(e);
        }
    }

    // Proxies drop silent connections after 30-60s; a comment line keeps them open (EventSource ignores it).
    @Scheduled(fixedRate = 15_000)
    void heartbeat() {
        streams.values().forEach(emitters -> emitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException e) {
                emitter.completeWithError(e);
            }
        }));
    }

    private void remove(QrSessionId id, SseEmitter emitter) {
        streams.computeIfPresent(id, (k, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }

    /**
     * @param serverTime EventSource hides response headers, so the client uses this instead of the
     *                   Date header to correct for clock skew.
     */
    record QrStatusEvent(String status, Instant expiresAt, String exchangeCode, Instant serverTime) {

        static QrStatusEvent of(QrStatusUpdate update, Clock clock) {
            return new QrStatusEvent(update.status().name(), update.expiresAt(), update.exchangeCode(),
                    clock.instant());
        }
    }
}
