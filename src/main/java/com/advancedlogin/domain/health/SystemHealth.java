package com.advancedlogin.domain.health;

public record SystemHealth(HealthStatus application, HealthStatus dataStore) {
}
