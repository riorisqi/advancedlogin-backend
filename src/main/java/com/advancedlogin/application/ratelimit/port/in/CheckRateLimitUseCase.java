package com.advancedlogin.application.ratelimit.port.in;

import com.advancedlogin.application.ratelimit.RateLimitDecision;

public interface CheckRateLimitUseCase {

    RateLimitDecision check(String rule, String subject);
}
