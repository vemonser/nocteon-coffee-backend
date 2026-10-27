package com.nocteon.nocteon_api.common.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.lettuce.core.RedisURI;

/**
 * Unit-level checks for the URI precedence that the Spring Data factory and the
 * Bucket4j client share. The integration context proves reachability; these
 * prove the right fields are actually applied.
 */
class RedisConfigTest {

    private final RedisConfig config = new RedisConfig();

    @BeforeEach
    void defaults() {
        ReflectionTestUtils.setField(config, "redisUrl", "");
        ReflectionTestUtils.setField(config, "springDataRedisUrl", "");
        ReflectionTestUtils.setField(config, "redisHost", "redis.internal");
        ReflectionTestUtils.setField(config, "redisPort", 6380);
        ReflectionTestUtils.setField(config, "redisPassword", "secret");
        ReflectionTestUtils.setField(config, "redisSsl", true);
        ReflectionTestUtils.setField(config, "redisDatabase", 2);
        ReflectionTestUtils.setField(config, "redisTimeout", Duration.ofSeconds(7));
    }

    @Test
    void hostConfigurationCarriesPasswordSslDatabaseAndTimeout() {
        RedisURI uri = config.buildRedisUri();

        assertThat(uri.getHost()).isEqualTo("redis.internal");
        assertThat(uri.getPort()).isEqualTo(6380);
        assertThat(uri.isSsl()).isTrue();
        assertThat(uri.getDatabase()).isEqualTo(2);
        assertThat(uri.getTimeout()).isEqualTo(Duration.ofSeconds(7));
        assertThat(new String(passwordOf(uri))).isEqualTo("secret");
    }

    @Test
    void redisUrlTakesPrecedenceOverHostAndPort() {
        ReflectionTestUtils.setField(config, "redisUrl", "redis://user:from-url@example.com:6381/3");

        RedisURI uri = config.buildRedisUri();

        assertThat(uri.getHost()).isEqualTo("example.com");
        assertThat(uri.getPort()).isEqualTo(6381);
        assertThat(uri.getDatabase()).isEqualTo(3);
        assertThat(uri.isSsl()).isFalse();
        assertThat(uri.getTimeout()).isEqualTo(Duration.ofSeconds(7));
        assertThat(new String(passwordOf(uri))).isEqualTo("from-url");
    }

    @Test
    void redissUrlEnablesSsl() {
        ReflectionTestUtils.setField(config, "redisUrl", "rediss://example.com:6380");

        RedisURI uri = config.buildRedisUri();

        assertThat(uri.isSsl()).isTrue();
        assertThat(uri.getHost()).isEqualTo("example.com");
    }

    @Test
    void springDataUrlIsUsedWhenRedisUrlIsBlank() {
        ReflectionTestUtils.setField(config, "springDataRedisUrl", "redis://spring-data:6382/4");

        RedisURI uri = config.buildRedisUri();

        assertThat(uri.getHost()).isEqualTo("spring-data");
        assertThat(uri.getPort()).isEqualTo(6382);
        assertThat(uri.getDatabase()).isEqualTo(4);
    }

    private char[] passwordOf(RedisURI uri) {
        var credentials = uri.getCredentialsProvider().resolveCredentials().block();
        assertThat(credentials).isNotNull();
        assertThat(credentials.hasPassword()).isTrue();
        return credentials.getPassword();
    }
}
