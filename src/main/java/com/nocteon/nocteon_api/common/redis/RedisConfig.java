package com.nocteon.nocteon_api.common.redis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;

@Configuration
public class RedisConfig {
    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    // ===== موجودة من قبل - لـ TokenBlacklistService =====
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        return template;
    }

    // ===== جديدة - لـ Bucket4j =====
    @Bean
    public RedisClient redisClient() {
        RedisURI.Builder uriBuilder = RedisURI.Builder.redis(redisHost, redisPort);

        if (redisPassword != null && !redisPassword.isBlank()) {
            uriBuilder.withPassword(redisPassword.toCharArray());
        }

        return RedisClient.create(uriBuilder.build());
    }
}