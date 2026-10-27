package com.nocteon.nocteon_api.common.ratelimit;

import java.time.Duration;

import org.springframework.stereotype.Service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.api.StatefulRedisConnection;

@Service
public class RateLimiterService {

    private final ProxyManager<String> proxyManager;

    public RateLimiterService(StatefulRedisConnection<String, byte[]> connection) {
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

    public boolean tryConsumeVerifyEmail(String ip) {
        Bucket bucket = proxyManager.builder().build("rate:verify-email:ip:" + ip, this::verifyEmailBucketConfig);
        return bucket.tryConsume(1);
    }

    private BucketConfiguration verifyEmailBucketConfig() {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(5)
                        .refillIntervally(5, Duration.ofHours(1))
                        .build())
                .build();
    }

    public boolean tryConsumeEmailStatus(String ip) {
        Bucket bucket = proxyManager.builder().build("rate:email-status:ip:" + ip, this::emailStatusBucketConfig);
        return bucket.tryConsume(1);
    }

    public boolean tryConsumeContact(String ip) {
        Bucket bucket = proxyManager.builder().build("rate:contact:ip:" + ip, this::contactBucketConfig);
        return bucket.tryConsume(1);
    }

    private BucketConfiguration emailStatusBucketConfig() {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(10)
                        .refillIntervally(10, Duration.ofMinutes(1))
                        .build())
                .build();
    }

    private BucketConfiguration contactBucketConfig() {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(5)
                        .refillIntervally(5, Duration.ofMinutes(10))
                        .build())
                .build();
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

    public boolean tryConsumeWebhook(String ip) {
        Bucket bucket = proxyManager.builder().build("rate:webhook:ip:" + ip, this::webhookBucketConfig);
        return bucket.tryConsume(1);
    }

    private BucketConfiguration webhookBucketConfig() {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(60)
                        .refillIntervally(60, Duration.ofMinutes(1))
                        .build())
                .build();
    }

    public boolean tryConsumeForgotPassword(String ip) {
        Bucket bucket = proxyManager.builder().build("rate:forgot-password:ip:" + ip, this::forgotPasswordBucketConfig);
        return bucket.tryConsume(1);
    }

    private BucketConfiguration forgotPasswordBucketConfig() {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(3)
                        .refillIntervally(3, Duration.ofHours(1))
                        .build())
                .build();
    }

    public boolean tryConsumeResetPassword(String ip) {
        Bucket bucket = proxyManager.builder().build("rate:reset-password:ip:" + ip, this::resetPasswordBucketConfig);
        return bucket.tryConsume(1);
    }

    private BucketConfiguration resetPasswordBucketConfig() {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(5)
                        .refillIntervally(5, Duration.ofHours(1))
                        .build())
                .build();
    }

    public boolean tryConsumeResendVerification(String ip) {
        Bucket bucket = proxyManager.builder().build("rate:resend-verification:ip:" + ip, this::resendVerificationBucketConfig);
        return bucket.tryConsume(1);
    }

    private BucketConfiguration resendVerificationBucketConfig() {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(3)
                        .refillIntervally(3, Duration.ofHours(1))
                        .build())
                .build();
    }

    public boolean tryConsumeNewsletterSubscribe(String ip) {
        Bucket bucket = proxyManager.builder().build("rate:newsletter:ip:" + ip, this::newsletterSubscribeBucketConfig);
        return bucket.tryConsume(1);
    }

    private BucketConfiguration newsletterSubscribeBucketConfig() {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(5)
                        .refillIntervally(5, Duration.ofHours(1))
                        .build())
                .build();
    }

    public boolean tryConsumeGeneral(String identifier) {
        Bucket bucket = proxyManager.builder().build("rate:general:" + identifier, this::generalBucketConfig);
        return bucket.tryConsume(1);
    }

    private BucketConfiguration generalBucketConfig() {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(200)
                        .refillIntervally(200, Duration.ofMinutes(1))
                        .build())
                .build();
    }
}