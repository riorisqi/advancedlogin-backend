package com.advancedlogin.application.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitServiceTest {

    private static final RateLimit TWO_PER_MINUTE = new RateLimit(2, Duration.ofMinutes(1));

    private final List<String> keys = new ArrayList<>();
    private final Map<String, Integer> counts = new HashMap<>();

    /** Counts per key forever: windows don't matter for the service. */
    private RateLimitDecision fakeHit(String key, RateLimit limit) {
        keys.add(key);
        int count = counts.merge(key, 1, Integer::sum);
        return new RateLimitDecision(count <= limit.maxRequests(), Duration.ofSeconds(42));
    }

    @Test
    void appliesTheNamedRulePerSubject() {
        RateLimitService service = new RateLimitService(this::fakeHit, Map.of("qr-create", TWO_PER_MINUTE), true);

        assertThat(service.check("qr-create", "10.0.0.1").allowed()).isTrue();
        assertThat(service.check("qr-create", "10.0.0.1").allowed()).isTrue();
        RateLimitDecision third = service.check("qr-create", "10.0.0.1");
        assertThat(third.allowed()).isFalse();
        assertThat(third.retryAfter()).isEqualTo(Duration.ofSeconds(42));

        assertThat(service.check("qr-create", "10.0.0.2").allowed()).as("other IP, own counter").isTrue();
        assertThat(keys).first().isEqualTo("qr-create:10.0.0.1");
    }

    @Test
    void disabledAllowsEverythingWithoutCounting() {
        RateLimitService service = new RateLimitService(this::fakeHit, Map.of("qr-create", TWO_PER_MINUTE), false);

        for (int i = 0; i < 10; i++) {
            assertThat(service.check("qr-create", "10.0.0.1").allowed()).isTrue();
        }
        assertThat(keys).isEmpty();
    }

    @Test
    void unknownRuleIsAProgrammingError() {
        RateLimitService service = new RateLimitService(this::fakeHit, Map.of(), true);

        assertThatThrownBy(() -> service.check("typo", "x")).isInstanceOf(IllegalArgumentException.class);
    }
}
