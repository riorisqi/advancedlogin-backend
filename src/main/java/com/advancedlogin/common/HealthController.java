package com.advancedlogin.common;

import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Smoke-test endpoint for milestone 1: proves the frontend -> Vite proxy -> Spring -> Redis
 * chain works. (Actuator's /actuator/health does the same in production apps; this one is
 * hand-written so the frontend has a tiny, stable JSON contract to consume.)
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final RedisConnectionFactory redisConnectionFactory;

    public HealthController(RedisConnectionFactory redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @GetMapping
    public HealthResponse health() {
        return new HealthResponse("UP", redisStatus());
    }

    private String redisStatus() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            return "PONG".equalsIgnoreCase(connection.ping()) ? "UP" : "DOWN";
        } catch (Exception e) {
            return "DOWN";
        }
    }

    public record HealthResponse(String status, String redis) {
    }
}
