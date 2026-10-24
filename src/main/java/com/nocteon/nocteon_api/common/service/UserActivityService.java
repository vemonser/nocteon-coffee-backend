package com.nocteon.nocteon_api.common.service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nocteon.nocteon_api.auth.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserActivityService {

    private final UserRepository userRepository;

    private static final long THROTTLE_SECONDS = 300;
    private final ConcurrentMap<Long, Instant> lastRecorded = new ConcurrentHashMap<>();

    @Transactional
    public void recordActivity(Long userId) {
        Instant now = Instant.now();
        Instant last = lastRecorded.get(userId);
        if (last != null && last.plusSeconds(THROTTLE_SECONDS).isAfter(now)) {
            return;
        }
        lastRecorded.put(userId, now);
        userRepository.updateLastActiveAt(userId, now);
    }
}
