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
 * The open SSE connections <em>on this backend instance</em>, grouped by QR session.
 *
 * <p>An SseEmitter is an HTTP response kept open: it lives in this JVM's memory, which is why
 * updates arrive via Redis Pub/Sub ({@link #deliver}) instead of straight from the use case.
 */
@Component
public class QrStatusStreams {

    /** Longer than any session can live (90+60+30+15s). If it runs out, EventSource just reconnects. */
    static final Duration STREAM_TIMEOUT = Duration.ofMinutes(4);

    private final Map<QrSessionId, Set<SseEmitter>> streams = new ConcurrentHashMap<>();
    private final Clock clock;

    QrStatusStreams(Clock clock) {
        this.clock = clock;
    }

    /** Registers a new stream. Callers must register <em>before</em> reading the initial snapshot (see controller). */
    SseEmitter open(QrSessionId id) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT.toMillis());
        streams.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet()).add(emitter);
        Runnable remove = () -> remove(id, emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        return emitter;
    }

    /** Called by the Redis subscriber for every status change on any instance. */
    public void deliver(QrStatusUpdate update) {
        Set<SseEmitter> emitters = streams.get(update.id());
        if (emitters == null) {
            return; // nobody on this instance is watching this QR
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
                emitter.complete(); // nothing more will happen: close the stream
            }
        } catch (IOException | IllegalStateException e) {
            // Client went away (tab closed, network dropped). Forget the stream.
            emitter.completeWithError(e);
        }
    }

    /**
     * SSE comment line every 15s. Proxies and load balancers often kill connections that stay
     * silent for 30-60s; a comment keeps it alive and is ignored by EventSource.
     */
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

    int openStreamCount() {
        return streams.values().stream().mapToInt(Set::size).sum();
    }

    private void remove(QrSessionId id, SseEmitter emitter) {
        streams.computeIfPresent(id, (k, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }

    /**
     * JSON in each {@code event: status} message.
     *
     * @param exchangeCode only for CONFIRMED: the one-time code to POST to /exchange.
     * @param serverTime the server's clock when the event was sent. EventSource doesn't expose
     *                   response headers, so the client can't use the Date header to correct
     *                   for clock skew the way the REST calls do; this field replaces it.
     */
    record QrStatusEvent(String status, Instant expiresAt, String exchangeCode, Instant serverTime) {

        static QrStatusEvent of(QrStatusUpdate update, Clock clock) {
            return new QrStatusEvent(update.status().name(), update.expiresAt(), update.exchangeCode(),
                    clock.instant());
        }
    }
}
