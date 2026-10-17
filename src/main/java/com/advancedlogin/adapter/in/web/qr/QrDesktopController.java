package com.advancedlogin.adapter.in.web.qr;

import com.advancedlogin.application.qr.port.in.CreateQrSessionUseCase;
import com.advancedlogin.application.qr.port.in.CreateQrSessionUseCase.CreatedQrSession;
import com.advancedlogin.application.qr.port.in.GetQrSessionStatusUseCase;
import com.advancedlogin.domain.qr.QrSession;
import com.advancedlogin.domain.qr.RequestingDevice;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;

/** Endpoints used by the desktop browser that wants to log in (anonymous). */
@RestController
class QrDesktopController {

    static final String BIND_COOKIE = "qr_bind";

    /**
     * Longer than any QR session can live (90s pending + 60s scanned + 30s confirmed), so the
     * cookie never disappears before the session does. Redis decides real expiry, not the cookie.
     */
    private static final Duration BIND_COOKIE_MAX_AGE = Duration.ofMinutes(5);

    private final CreateQrSessionUseCase createQrSession;
    private final GetQrSessionStatusUseCase getQrSessionStatus;
    private final String approveBaseUrl;
    private final boolean secureCookies;

    QrDesktopController(CreateQrSessionUseCase createQrSession,
                        GetQrSessionStatusUseCase getQrSessionStatus,
                        @Value("${app.qr.approve-base-url}") String approveBaseUrl,
                        @Value("${app.qr.secure-cookies:false}") boolean secureCookies) {
        this.createQrSession = createQrSession;
        this.getQrSessionStatus = getQrSessionStatus;
        this.approveBaseUrl = approveBaseUrl;
        this.secureCookies = secureCookies;
    }

    @PostMapping("/api/qr/sessions")
    ResponseEntity<CreateQrSessionResponse> create(HttpServletRequest request) {
        RequestingDevice device = new RequestingDevice(request.getRemoteAddr(), request.getHeader(HttpHeaders.USER_AGENT));
        CreatedQrSession created = createQrSession.create(device);
        QrSession session = created.session();

        // Binds this QR to this browser. HttpOnly: page JS can't read it. Path=/api/qr: only sent
        // to QR endpoints. SameSite=Strict: never sent on requests started by another site.
        // A new QR replaces the cookie, so only the newest QR in this browser can complete.
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
                .body(new CreateQrSessionResponse(qrId, approveBaseUrl + "/m/approve/" + qrId, session.expiresAt()));
    }

    /**
     * Status snapshot for polling. Requires this browser's qr_bind cookie: knowing the qrId
     * (it's on screen!) isn't enough. Missing cookie, wrong cookie, unknown or expired id all
     * give the same 404, which the desktop treats as "expired".
     */
    @GetMapping("/api/qr/sessions/{qrId}")
    QrStatusResponse status(@PathVariable String qrId,
                            @CookieValue(name = BIND_COOKIE, required = false) String bindingSecret) {
        QrSession session = getQrSessionStatus.getStatus(QrIds.parse(qrId), bindingSecret);
        return new QrStatusResponse(session.status().name(), session.expiresAt());
    }

    /** Deliberately minimal: the desktop learns nothing about *who* scanned until login completes. */
    record QrStatusResponse(String status, Instant expiresAt) {
    }

    /**
     * @param qrContent what the QR encodes: an https URL, so even the phone's built-in camera app
     *                  opens our approve page; our own scanner just extracts the id from it.
     */
    record CreateQrSessionResponse(String qrId, String qrContent, Instant expiresAt) {
    }
}
