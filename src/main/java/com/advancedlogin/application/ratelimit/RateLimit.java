package com.advancedlogin.application.ratelimit;

import java.time.Duration;
import java.util.Objects;

public record RateLimit(int maxRequests, Duration window) {

    public RateLimit {
        Objects.requireNonNull(window, "window");
        if (maxRequests < 1) {
            throw new IllegalArgumentException("maxRequests must be >= 1");
        }
        if (window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("window must be positive");
        }
    }
}
