package com.advancedlogin.application.ratelimit;

import com.advancedlogin.application.ratelimit.port.in.CheckRateLimitUseCase;
import com.advancedlogin.application.ratelimit.port.out.RateLimiter;

import java.util.Map;

public class RateLimitService implements CheckRateLimitUseCase {

    private final RateLimiter limiter;
    private final Map<String, RateLimit> rules;
    private final boolean enabled;

    /** @param enabled false allows everything (most integration tests) */
    public RateLimitService(RateLimiter limiter, Map<String, RateLimit> rules, boolean enabled) {
        this.limiter = limiter;
        this.rules = Map.copyOf(rules);
        this.enabled = enabled;
    }

    @Override
    public RateLimitDecision check(String rule, String subject) {
        RateLimit limit = rules.get(rule);
        if (limit == null) {
            throw new IllegalArgumentException("Unknown rate limit rule: " + rule);
        }
        if (!enabled) {
            return RateLimitDecision.allow();
        }
        return limiter.hit(rule + ":" + subject, limit);
    }
}
