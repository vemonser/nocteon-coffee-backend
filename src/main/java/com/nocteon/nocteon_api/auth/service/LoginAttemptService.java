package com.nocteon.nocteon_api.auth.service;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.nocteon.nocteon_api.auth.entity.User;
import com.nocteon.nocteon_api.auth.repository.UserRepository;
import com.nocteon.nocteon_api.common.exception.account.AccountLockedException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class LoginAttemptService {

    private final UserRepository userRepository;

    @Value("${application.security.lockout.max-attempts:5}")
    private int maxFailedAttempts;

    public void handleFailedLogin(User user) {
        user.incrementFailedLogin();
        userRepository.save(user);

        if (user.isLocked()) {
            log.warn("Account locked for user {} after {} failed attempts",
                    user.getEmail(), user.getFailedLoginAttempts());
        } else {
            int remaining = maxFailedAttempts - user.getFailedLoginAttempts();
            log.warn("Failed login attempt {} for user {}. {} attempts remaining",
                    user.getFailedLoginAttempts(), user.getEmail(), remaining);
        }
    }

    public void checkAccountNotLocked(User user) {
        if (user.isLocked()) {
            long remainingMinutes = Math.max(
                    1,
                    Duration.between(
                            Instant.now(),
                            user.getLockedUntil()).toMinutes());
            throw new AccountLockedException(remainingMinutes);
        }
    }
}