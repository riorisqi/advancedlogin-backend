package com.advancedlogin.adapter.in.web.health;

import com.advancedlogin.application.health.port.in.CheckHealthUseCase;
import com.advancedlogin.domain.health.SystemHealth;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
class HealthController {

    private final CheckHealthUseCase checkHealth;

    HealthController(CheckHealthUseCase checkHealth) {
        this.checkHealth = checkHealth;
    }

    @GetMapping
    HealthResponse health() {
        return HealthResponse.from(checkHealth.check());
    }

    record HealthResponse(String status, String redis) {

        static HealthResponse from(SystemHealth health) {
            return new HealthResponse(health.application().name(), health.dataStore().name());
        }
    }
}
