package com.nocteon.nocteon_api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.nocteon.nocteon_api.auth.entity.User;

class UserLockoutTest {

    @Test
    void lockoutThresholdAndDurationComeFromTheCaller() {
        User user = User.builder().build();

        user.incrementFailedLogin(3, Duration.ofMinutes(7));
        user.incrementFailedLogin(3, Duration.ofMinutes(7));
        assertThat(user.isLocked()).isFalse();

        Instant before = Instant.now();
        user.incrementFailedLogin(3, Duration.ofMinutes(7));

        assertThat(user.isLocked()).isTrue();
        assertThat(user.getLockedUntil()).isAfter(before.plus(Duration.ofMinutes(6)));
    }
}
