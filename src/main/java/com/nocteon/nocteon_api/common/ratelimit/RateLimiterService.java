package com.nocteon.nocteon_api.common.ratelimit;

import java.time.Duration;

import org.springframework.stereotype.Service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final RedisClient redisClient;
    private ProxyManager<String> proxyManager;

    @PostConstruct
    public void init() {
        StatefulRedisConnection<String, byte[]> connection = redisClient
                .connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));

        this.proxyManager = LettuceBasedProxyManager
                .builderFor(connection)
                .build();
    }

    public boolean tryConsumeLogin(String ip, String identifier) {
        Bucket ipBucket = proxyManager.builder().build("rate:login:ip:" + ip, this::loginBucketConfig);
        Bucket identifierBucket = proxyManager.builder().build("rate:login:id:" + identifier, this::loginBucketConfig);

        return ipBucket.tryConsume(1) && identifierBucket.tryConsume(1);
    }

    public boolean tryConsumeRegister(String ip) {
        Bucket bucket = proxyManager.builder().build("rate:register:ip:" + ip, this::registerBucketConfig);
        return bucket.tryConsume(1);
    }

    private BucketConfiguration loginBucketConfig() {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(5)
                        .refillIntervally(5, Duration.ofMinutes(1))
                        .build())
                .build();
    }

    private BucketConfiguration registerBucketConfig() {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(3)
                        .refillIntervally(3, Duration.ofMinutes(10))
                        .build())
                .build();
    }
}