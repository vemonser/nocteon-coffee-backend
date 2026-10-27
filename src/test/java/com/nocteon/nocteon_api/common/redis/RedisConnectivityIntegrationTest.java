package com.nocteon.nocteon_api.common.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.common.ratelimit.RateLimiterService;
import com.nocteon.nocteon_api.support.TestJwtKeys;

/**
 * The context only starts if RedisConfig's startup PING succeeds, so a passing
 * context is already proof that the configured target is reachable. These tests
 * additionally exercise both clients that use Redis: Spring Data's connection
 * factory and Bucket4j's shared Lettuce connection.
 */
@SpringBootTest(classes = NocteonApiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RedisConnectivityIntegrationTest {

    @Autowired
    private RedisConnectionFactory connectionFactory;

    @Autowired
    private RateLimiterService rateLimiterService;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    @Test
    void springDataConnectionAnswersPing() {
        try (var connection = connectionFactory.getConnection()) {
            assertThat(connection.ping()).isEqualToIgnoringCase("PONG");
        }
    }

    @Test
    void bucket4jSharedConnectionCanConsumeABucket() {
        String identifier = "redis-test:" + UUID.randomUUID();
        assertThat(rateLimiterService.tryConsumeGeneral(identifier)).isTrue();
    }
}
