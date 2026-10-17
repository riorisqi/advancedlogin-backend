package com.advancedlogin.domain.health;

/**
 * Health of the application and the data store it depends on.
 * Domain objects are plain Java: no Spring, no Redis, no JSON annotations.
 */
public record SystemHealth(HealthStatus application, HealthStatus dataStore) {
}
