package com.advancedlogin.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.GenericContainer;

/**
 * Base class for tests that run the real app on a random port against a throwaway Redis in Docker.
 *
 * <p>Singleton container: started once in a static initializer and shared by every subclass
 * (Testcontainers' Ryuk sidecar removes it when the JVM exits). Because all subclasses share the
 * same configuration, Spring also reuses one application context across them.
 */
// Rate limiting off: these tests fire far more requests per minute from one IP than any human.
// RateLimitIntegrationTest switches it back on with tiny limits.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.rate-limit.enabled=false")
public abstract class RedisIntegrationTest {

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);

    static {
        REDIS.start();
    }
}
