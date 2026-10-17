package com.advancedlogin.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.GenericContainer;

/**
 * The real app on a random port against a throwaway Redis. One container for all subclasses
 * (Ryuk removes it at JVM exit). Rate limiting is off: these tests fire far more requests per
 * minute than a human; RateLimitIntegrationTest turns it back on.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.rate-limit.enabled=false")
public abstract class RedisIntegrationTest {

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);

    static {
        REDIS.start();
    }
}
