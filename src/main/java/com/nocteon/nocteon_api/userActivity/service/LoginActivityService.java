package com.nocteon.nocteon_api.userActivity.service;

import org.springframework.stereotype.Service;

import com.nocteon.nocteon_api.auth.entity.User;
import com.nocteon.nocteon_api.userActivity.entity.LoginActivity;
import com.nocteon.nocteon_api.userActivity.enums.DeviceType;
import com.nocteon.nocteon_api.userActivity.repository.LoginActivityRepository;
import com.nocteon.nocteon_api.userActivity.resolver.DeviceTypeResolver;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LoginActivityService {

    private final LoginActivityRepository loginActivityRepository;
    private final DeviceTypeResolver deviceTypeResolver;

    @Transactional
    public void recordLogin(User user, String userAgent) {
        DeviceType deviceType = deviceTypeResolver.resolve(userAgent);

        LoginActivity activity = LoginActivity.builder()
                .user(user)
                .deviceType(deviceType)
                .build();

        loginActivityRepository.save(activity);
    }
}