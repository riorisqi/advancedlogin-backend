package com.advancedlogin.application.ratelimit.port.in;

import com.advancedlogin.application.ratelimit.RateLimitDecision;

public interface CheckRateLimitUseCase {

    /**
     * Counts one request for {@code subject} (an IP address, a username, ...) under the named
     * {@code rule} (e.g. "qr-create") and decides whether it may proceed.
     */
    RateLimitDecision check(String rule, String subject);
}
