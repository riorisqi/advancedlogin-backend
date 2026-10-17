package com.advancedlogin.adapter.in.web.qr;

import com.advancedlogin.adapter.in.web.security.SessionUser;
import com.advancedlogin.application.qr.port.in.ApproveQrSessionUseCase;
import com.advancedlogin.application.qr.port.in.RejectQrSessionUseCase;
import com.advancedlogin.application.qr.port.in.ScanQrSessionUseCase;
import com.advancedlogin.domain.qr.QrSession;
import com.advancedlogin.domain.user.UserId;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** Phone endpoints: the phone's own session (plus CSRF token) vouches for the user, never a password. */
@RestController
@RequestMapping("/api/qr/sessions/{qrId}")
class QrMobileController {

    private final ScanQrSessionUseCase scanQrSession;
    private final ApproveQrSessionUseCase approveQrSession;
    private final RejectQrSessionUseCase rejectQrSession;

    QrMobileController(ScanQrSessionUseCase scanQrSession, ApproveQrSessionUseCase approveQrSession,
                       RejectQrSessionUseCase rejectQrSession) {
        this.scanQrSession = scanQrSession;
        this.approveQrSession = approveQrSession;
        this.rejectQrSession = rejectQrSession;
    }

    @PostMapping("/scan")
    LoginRequestResponse scan(@PathVariable String qrId, @AuthenticationPrincipal SessionUser user) {
        QrSession session = scanQrSession.scan(QrIds.parse(qrId), userId(user));
        return LoginRequestResponse.from(session);
    }

    @PostMapping("/approve")
    ResponseEntity<Void> approve(@PathVariable String qrId, @AuthenticationPrincipal SessionUser user) {
        approveQrSession.approve(QrIds.parse(qrId), userId(user));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reject")
    ResponseEntity<Void> reject(@PathVariable String qrId, @AuthenticationPrincipal SessionUser user) {
        rejectQrSession.reject(QrIds.parse(qrId), userId(user));
        return ResponseEntity.noContent().build();
    }

    private static UserId userId(SessionUser user) {
        return new UserId(user.userId());
    }

    /** Shown before approving, so a QR relayed from someone else's computer can be spotted (QRLjacking). */
    record LoginRequestResponse(String qrId, Instant requestedAt, String browser, String os, String ipAddress,
                                String approximateLocation, Instant expiresAt) {

        static LoginRequestResponse from(QrSession session) {
            UserAgentSummary ua = UserAgentSummary.parse(session.requestingDevice().userAgent());
            return new LoginRequestResponse(session.id().value(), session.createdAt(), ua.browser(), ua.os(),
                    session.requestingDevice().ipAddress(), session.requestingDevice().approximateLocation(),
                    session.expiresAt());
        }
    }
}
