package com.nocteon.nocteon_api.common.redis;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class RedisConfig {

    @Value("${REDIS_URL:}")
    private String redisUrl;

    @Value("${spring.data.redis.url:}")
    private String springDataRedisUrl;

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.ssl.enabled:false}")
    private boolean redisSsl;

    @Value("${spring.data.redis.database:0}")
    private int redisDatabase;

    @Value("${spring.data.redis.timeout:5s}")
    private Duration redisTimeout;

    /**
     * One connection factory for Spring Data, built from the same parsed URI as the
     * Bucket4j client. This keeps token-blacklist/chat storage on the same target
     * when only REDIS_URL is configured; relying on Boot's host/port auto-config
     * would silently point them at localhost.
     */
    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        RedisURI uri = buildRedisUri();
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalStateException("Redis URI must contain a host (socket URIs are not supported)");
        }
        RedisStandaloneConfiguration standalone =
                new RedisStandaloneConfiguration(uri.getHost(), uri.getPort());
        standalone.setDatabase(uri.getDatabase());
        if (uri.getCredentialsProvider() != null) {
            var credentials = uri.getCredentialsProvider().resolveCredentials().block();
            if (credentials != null) {
                if (credentials.hasUsername()) {
                    standalone.setUsername(credentials.getUsername());
                }
                if (credentials.hasPassword()) {
                    standalone.setPassword(RedisPassword.of(credentials.getPassword()));
                }
            }
        }
        Duration timeout = uri.getTimeout() != null ? uri.getTimeout() : Duration.ofSeconds(5);
        LettuceClientConfiguration.LettuceClientConfigurationBuilder client =
                LettuceClientConfiguration.builder().commandTimeout(timeout);
        if (uri.isSsl()) {
            client.useSsl();
        }
        return new LettuceConnectionFactory(standalone, client.build());
    }

    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        return template;
    }

    /**
     * Bucket4j needs a raw Lettuce client. Build it from the same URL/host/port/
     * password/SSL/timeout settings as Spring Data Redis, instead of a separate
     * hand-rolled configuration that ignored {@code REDIS_SSL} and {@code REDIS_URL}.
     * The client is closed by Spring on shutdown.
     */
    @Bean(destroyMethod = "shutdown")
    public RedisClient redisClient() {
        RedisURI uri = buildRedisUri();
        log.info("Redis client target: {}:{} (ssl={}, database={})",
                uri.getHost() != null ? uri.getHost() : uri.getSocket(),
                uri.getPort(), uri.isSsl(), uri.getDatabase());
        return RedisClient.create(uri);
    }

    /**
     * Opens the connection during startup and PINGs it, so a bad Redis target
     * fails fast with a clear message instead of surfacing on the first request.
     */
    @Bean(destroyMethod = "close")
    public StatefulRedisConnection<String, byte[]> bucket4jRedisConnection(
            RedisClient redisClient, RedisConnectionFactory springDataConnectionFactory) {
        StatefulRedisConnection<String, byte[]> connection = null;
        try {
            try (var springData = springDataConnectionFactory.getConnection()) {
                if (!"PONG".equalsIgnoreCase(springData.ping())) {
                    throw new IllegalStateException("unexpected Spring Data PING reply");
                }
            }
            log.info("Spring Data Redis startup PING succeeded");

            connection = redisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
            String pong = connection.sync().ping();
            if (!"PONG".equalsIgnoreCase(pong)) {
                throw new IllegalStateException("unexpected PING reply: " + pong);
            }
            log.info("Redis startup PING succeeded");
            return connection;
        } catch (RuntimeException e) {
            if (connection != null) {
                connection.close();
            }
            throw new IllegalStateException(
                    "Redis is unreachable. Check REDIS_URL or REDIS_HOST/REDIS_PORT/"
                            + "REDIS_PASSWORD/REDIS_SSL (or spring.data.redis.*).",
                    e);
        }
    }

    RedisURI buildRedisUri() {
        String url = redisUrl != null && !redisUrl.isBlank() ? redisUrl : springDataRedisUrl;
        if (url != null && !url.isBlank()) {
            RedisURI uri = RedisURI.create(url);
            if (redisTimeout != null) {
                uri.setTimeout(redisTimeout);
            }
            return uri;
        }

        RedisURI.Builder builder = RedisURI.builder()
                .withHost(redisHost)
                .withPort(redisPort)
                .withSsl(redisSsl)
                .withDatabase(redisDatabase)
                .withTimeout(redisTimeout);

        if (redisPassword != null && !redisPassword.isBlank()) {
            builder.withPassword(redisPassword.toCharArray());
        }
        return builder.build();
    }
}
