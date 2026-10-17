package com.advancedlogin.config;

import com.advancedlogin.application.health.CheckHealthService;
import com.advancedlogin.application.health.port.in.CheckHealthUseCase;
import com.advancedlogin.application.health.port.out.DataStoreHealthPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composition root for use cases: the only place that knows both the application services
 * and Spring. Adapters are picked up by component scanning; use cases are registered here.
 */
@Configuration
class UseCaseConfig {

    @Bean
    CheckHealthUseCase checkHealthUseCase(DataStoreHealthPort dataStoreHealthPort) {
        return new CheckHealthService(dataStoreHealthPort);
    }
}
