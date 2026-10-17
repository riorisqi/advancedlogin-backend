package com.advancedlogin.application.ratelimit.port.out;

import com.advancedlogin.application.ratelimit.RateLimit;
import com.advancedlogin.application.ratelimit.RateLimitDecision;

/** Counts hits per key in a time window. Implemented with Redis so all backend instances share the counts. */
public interface RateLimiter {

    /** Records one hit for {@code key} and says whether it's still within {@code limit}. */
    RateLimitDecision hit(String key, RateLimit limit);
}
