package com.advancedlogin.domain.health;

public enum HealthStatus {
    UP,
    DOWN;

    public static HealthStatus of(boolean healthy) {
        return healthy ? UP : DOWN;
    }
}
