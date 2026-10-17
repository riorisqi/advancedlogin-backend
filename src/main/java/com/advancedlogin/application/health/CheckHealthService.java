package com.advancedlogin.application.health;

import com.advancedlogin.application.health.port.in.CheckHealthUseCase;
import com.advancedlogin.application.health.port.out.DataStoreHealthPort;
import com.advancedlogin.domain.health.HealthStatus;
import com.advancedlogin.domain.health.SystemHealth;

/**
 * Use case implementation. Deliberately free of Spring annotations: it is wired as a bean in
 * {@code config.UseCaseConfig}, so it can be unit-tested with a plain fake port.
 */
public class CheckHealthService implements CheckHealthUseCase {

    private final DataStoreHealthPort dataStoreHealth;

    public CheckHealthService(DataStoreHealthPort dataStoreHealth) {
        this.dataStoreHealth = dataStoreHealth;
    }

    @Override
    public SystemHealth check() {
        return new SystemHealth(HealthStatus.UP, HealthStatus.of(dataStoreHealth.isReachable()));
    }
}
