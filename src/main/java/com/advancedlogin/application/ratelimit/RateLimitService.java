package com.advancedlogin.application.ratelimit;

import com.advancedlogin.application.ratelimit.port.in.CheckRateLimitUseCase;
import com.advancedlogin.application.ratelimit.port.out.RateLimiter;

import java.util.Map;

/** Named rules ("qr-create" → 10 per minute, ...) on top of the raw {@link RateLimiter}. */
public class RateLimitService implements CheckRateLimitUseCase {

    private final RateLimiter limiter;
    private final Map<String, RateLimit> rules;
    private final boolean enabled;

    /**
     * @param enabled false turns every check into "allowed" (used by most integration tests, which
     *                fire far more requests per minute than any human would)
     */
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
