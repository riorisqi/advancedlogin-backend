package com.advancedlogin.application.ratelimit;

import java.time.Duration;

/**
 * @param retryAfter how long until the current window resets (meaningful when not allowed;
 *                   sent to the client as the {@code Retry-After} header)
 */
public record RateLimitDecision(boolean allowed, Duration retryAfter) {

    public static RateLimitDecision allow() {
        return new RateLimitDecision(true, Duration.ZERO);
    }
}
