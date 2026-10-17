package com.advancedlogin.adapter.in.web.qr;

import com.advancedlogin.adapter.in.web.security.WebSessionAuthenticator;
import com.advancedlogin.application.qr.port.in.CreateQrSessionUseCase;
import com.advancedlogin.application.qr.port.in.CreateQrSessionUseCase.CreatedQrSession;
import com.advancedlogin.application.qr.port.in.ExchangeQrCodeUseCase;
import com.advancedlogin.application.qr.port.in.GetQrSessionStatusUseCase;
import com.advancedlogin.domain.qr.QrSession;
import com.advancedlogin.domain.qr.QrSessionId;
import com.advancedlogin.domain.qr.QrStatusUpdate;
import com.advancedlogin.domain.qr.RequestingDevice;
import com.advancedlogin.domain.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;

/** Endpoints used by the desktop browser that wants to log in (anonymous). */
@RestController
class QrDesktopController {

    static final String BIND_COOKIE = "qr_bind";

    // Outlives any session (90+60+30s); the Redis TTL decides real expiry, not the cookie.
    private static final Duration BIND_COOKIE_MAX_AGE = Duration.ofMinutes(5);

    private final CreateQrSessionUseCase createQrSession;
    private final GetQrSessionStatusUseCase getQrSessionStatus;
    private final QrStatusStreams streams;
    private final ExchangeQrCodeUseCase exchangeQrCode;
    private final WebSessionAuthenticator sessionAuthenticator;
    private final String approveBaseUrl;
    private final boolean secureCookies;

    QrDesktopController(CreateQrSessionUseCase createQrSession,
                        GetQrSessionStatusUseCase getQrSessionStatus,
                        QrStatusStreams streams,
                        ExchangeQrCodeUseCase exchangeQrCode,
                        WebSessionAuthenticator sessionAuthenticator,
                        @Value("${app.qr.approve-base-url:}") String approveBaseUrl,
                        @Value("${app.qr.secure-cookies:false}") boolean secureCookies) {
        this.createQrSession = createQrSession;
        this.getQrSessionStatus = getQrSessionStatus;
        this.streams = streams;
        this.exchangeQrCode = exchangeQrCode;
        this.sessionAuthenticator = sessionAuthenticator;
        this.approveBaseUrl = approveBaseUrl;
        this.secureCookies = secureCookies;
    }

    @PostMapping("/api/qr/sessions")
    ResponseEntity<CreateQrSessionResponse> create(HttpServletRequest request) {
        RequestingDevice device = new RequestingDevice(request.getRemoteAddr(), request.getHeader(HttpHeaders.USER_AGENT));
        CreatedQrSession created = createQrSession.create(device);
        QrSession session = created.session();

        // Binds the QR to this browser: HttpOnly (no JS access), only sent to /api/qr, never cross-site.
        ResponseCookie bindCookie = ResponseCookie.from(BIND_COOKIE, created.bindingSecret())
                .httpOnly(true)
                .secure(secureCookies)
                .sameSite("Strict")
                .path("/api/qr")
                .maxAge(BIND_COOKIE_MAX_AGE)
                .build();

        String qrId = session.id().value();
        return ResponseEntity.created(URI.create("/api/qr/sessions/" + qrId))
                .header(HttpHeaders.SET_COOKIE, bindCookie.toString())
                .body(new CreateQrSessionResponse(qrId, ApproveLinks.forQr(qrId, approveBaseUrl, request.getHeader(HttpHeaders.ORIGIN)),
                        session.expiresAt()));
    }

    /** Needs this browser's qr_bind cookie: the qrId alone is visible on screen. Any failure is the same 404. */
    @GetMapping("/api/qr/sessions/{qrId}")
    QrStatusResponse status(@PathVariable String qrId,
                            @CookieValue(name = BIND_COOKIE, required = false) String bindingSecret) {
        QrSession session = getQrSessionStatus.getStatus(QrIds.parse(qrId), bindingSecret);
        return new QrStatusResponse(session.status().name(), session.expiresAt(), session.exchangeCode());
    }

    /** Trades the one-time code (plus qr_bind) for a real session, exactly like a password login. */
    @PostMapping("/api/qr/sessions/{qrId}/exchange")
    ResponseEntity<SignedInUserResponse> exchange(@PathVariable String qrId,
                                                  @CookieValue(name = BIND_COOKIE, required = false) String bindingSecret,
                                                  @Valid @RequestBody ExchangeRequest body,
                                                  HttpServletRequest request, HttpServletResponse response) {
        User user = exchangeQrCode.exchange(QrIds.parse(qrId), bindingSecret, body.code());
        sessionAuthenticator.logIn(user, request, response);

        ResponseCookie clearBind = ResponseCookie.from(BIND_COOKIE, "")
                .httpOnly(true).secure(secureCookies).sameSite("Strict").path("/api/qr").maxAge(0).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearBind.toString())
                .body(new SignedInUserResponse(user.id().value(), user.username(), user.displayName()));
    }

    record ExchangeRequest(@NotBlank String code) {
    }

    record SignedInUserResponse(String id, String username, String displayName) {
    }

    @GetMapping(path = "/api/qr/sessions/{qrId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter events(@PathVariable String qrId,
                      @CookieValue(name = BIND_COOKIE, required = false) String bindingSecret) {
        QrSessionId id = QrIds.parse(qrId);
        // Check the binding first, so strangers never get a stream registered.
        getQrSessionStatus.getStatus(id, bindingSecret);

        // Register first, then read the snapshot: an update landing in between is sent twice
        // (harmless), whereas the other order could lose it.
        SseEmitter emitter = streams.open(id);
        try {
            QrSession snapshot = getQrSessionStatus.getStatus(id, bindingSecret);
            streams.send(emitter, QrStatusUpdate.of(snapshot));
        } catch (RuntimeException e) {
            emitter.complete(); // expired in the meantime
        }
        return emitter;
    }

    /** Says nothing about who scanned. exchangeCode is only set while CONFIRMED. */
    record QrStatusResponse(String status, Instant expiresAt, String exchangeCode) {
    }

    /** qrContent is a URL, so the phone's own camera app can open it too. */
    record CreateQrSessionResponse(String qrId, String qrContent, Instant expiresAt) {
    }
}
