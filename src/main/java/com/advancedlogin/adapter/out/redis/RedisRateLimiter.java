package com.advancedlogin.adapter.out.redis;

import com.advancedlogin.application.ratelimit.RateLimit;
import com.advancedlogin.application.ratelimit.RateLimitDecision;
import com.advancedlogin.application.ratelimit.port.out.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/** Fixed-window counters in Redis ({@code rl:*}), shared by every backend instance. */
@Component
class RedisRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> HIT = RedisScript.of(new ClassPathResource("lua/rate_limit.lua"), List.class);

    private final StringRedisTemplate redis;

    RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public RateLimitDecision hit(String key, RateLimit limit) {
        try {
            List<?> result = redis.execute(HIT, List.of("rl:" + key), Long.toString(limit.window().toMillis()));
            long count = ((Number) result.get(0)).longValue();
            long ttlMillis = ((Number) result.get(1)).longValue();
            return new RateLimitDecision(count <= limit.maxRequests(), Duration.ofMillis(ttlMillis));
        } catch (RuntimeException e) {
            // Fail open: a Redis hiccup shouldn't lock everyone out (sessions need Redis anyway).
            log.warn("Rate limiter unavailable, allowing request", e);
            return RateLimitDecision.allow();
        }
    }
}
