package com.advancedlogin.application.ratelimit.port.out;

import com.advancedlogin.application.ratelimit.RateLimit;
import com.advancedlogin.application.ratelimit.RateLimitDecision;

public interface RateLimiter {

    RateLimitDecision hit(String key, RateLimit limit);
}
