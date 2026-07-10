package com.nocteon.nocteon_api.mail.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class EmailRetryPolicy {

    private static final List<Duration> BACKOFF_SCHEDULE = List.of(
            Duration.ZERO,
            Duration.ofMinutes(1),
            Duration.ofMinutes(5),
            Duration.ofMinutes(30),
            Duration.ofHours(2)
    );

    private static final int MAX_ATTEMPTS = BACKOFF_SCHEDULE.size();

    public boolean isDead(int attemptCount) {
        return attemptCount >= MAX_ATTEMPTS;
    }

    public boolean isDueForRetry(int attemptCount, Instant updatedAt) {
        if (isDead(attemptCount)) return false;
        Duration requiredWait = BACKOFF_SCHEDULE.get(attemptCount);
        return Instant.now().isAfter(updatedAt.plus(requiredWait));
    }
}