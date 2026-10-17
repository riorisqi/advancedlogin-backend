package com.advancedlogin.application.qr.port.out;

/** Unguessable tokens (QR ids, binding secrets, later exchange codes). A port so tests can be deterministic. */
public interface RandomTokenGenerator {

    /** 256 bits from a cryptographically secure source, base64url-encoded without padding (43 chars). */
    String newToken();
}
