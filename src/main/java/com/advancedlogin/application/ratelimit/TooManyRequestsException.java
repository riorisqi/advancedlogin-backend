package com.advancedlogin.application.ratelimit;

import java.time.Duration;

/** A use case refused because a rate limit was hit. Maps to HTTP 429 + Retry-After. */
public class TooManyRequestsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyRequestsException(Duration retryAfter) {
        super("Too many attempts. Try again in " + Math.max(1, retryAfter.toSeconds()) + " seconds.");
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
