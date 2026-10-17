package com.advancedlogin.config;

import com.advancedlogin.adapter.in.web.ratelimit.RateLimitFilter;
import com.advancedlogin.application.ratelimit.RateLimit;
import com.advancedlogin.application.ratelimit.RateLimitService;
import com.advancedlogin.application.ratelimit.port.in.CheckRateLimitUseCase;
import com.advancedlogin.application.ratelimit.port.out.RateLimiter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/** Rate-limit rules from {@code app.rate-limit.*} and the filter that applies the per-IP ones. */
@Configuration
@EnableConfigurationProperties(RateLimitConfig.RateLimitProperties.class)
class RateLimitConfig {

    @Bean
    CheckRateLimitUseCase checkRateLimitUseCase(RateLimiter limiter, RateLimitProperties properties) {
        Map<String, RateLimit> rules = new HashMap<>();
        properties.rules().forEach((name, rule) -> rules.put(name, new RateLimit(rule.requests(), rule.window())));
        return new RateLimitService(limiter, rules, properties.enabled());
    }

    /**
     * Registered by hand (not as @Component) to control its position: just before Spring
     * Security's filter chain, so rejected floods never reach session or CSRF handling.
     */
    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilter(CheckRateLimitUseCase rateLimits) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(new RateLimitFilter(rateLimits));
        registration.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER - 1);
        return registration;
    }

    @ConfigurationProperties(prefix = "app.rate-limit")
    record RateLimitProperties(boolean enabled, Map<String, Rule> rules) {

        RateLimitProperties {
            rules = rules == null ? Map.of() : Map.copyOf(rules);
        }

        record Rule(int requests, Duration window) {
        }
    }
}
