package com.advancedlogin.application.qr.port.out;

public interface RandomTokenGenerator {

    /** 256 random bits, base64url without padding (43 chars). */
    String newToken();
}
