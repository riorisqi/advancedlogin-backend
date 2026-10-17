package com.advancedlogin.application.health;

import com.advancedlogin.domain.health.HealthStatus;
import com.advancedlogin.domain.health.SystemHealth;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Plain unit test: no Spring context, no Redis. The outbound port is just a lambda. */
class CheckHealthServiceTest {

    @Test
    void reportsDataStoreUpWhenReachable() {
        SystemHealth health = new CheckHealthService(() -> true).check();

        assertThat(health).isEqualTo(new SystemHealth(HealthStatus.UP, HealthStatus.UP));
    }

    @Test
    void reportsDataStoreDownWhenUnreachable() {
        SystemHealth health = new CheckHealthService(() -> false).check();

        assertThat(health).isEqualTo(new SystemHealth(HealthStatus.UP, HealthStatus.DOWN));
    }
}
