package com.nocteon.nocteon_api.common.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nocteon.nocteon_api.auth.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserActivityService {

    private final UserRepository userRepository;

    @Transactional
    public void recordActivity(Long userId) {
        userRepository.updateLastActiveAt(userId, Instant.now());
    }
}