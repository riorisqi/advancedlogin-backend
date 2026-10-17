package com.advancedlogin.application.health.port.out;

/**
 * Outbound port: what the application needs from infrastructure. The application owns this
 * interface; the Redis adapter implements it. That inversion is what keeps Redis out of the
 * use case.
 */
public interface DataStoreHealthPort {

    boolean isReachable();
}
