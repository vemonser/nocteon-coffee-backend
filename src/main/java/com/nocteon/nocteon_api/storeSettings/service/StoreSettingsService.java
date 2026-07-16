package com.nocteon.nocteon_api.storeSettings.service;

import org.springframework.stereotype.Service;

import com.nocteon.nocteon_api.storeSettings.dto.request.StoreSettingsRequest;
import com.nocteon.nocteon_api.storeSettings.dto.response.StoreSettingsResponse;
import com.nocteon.nocteon_api.storeSettings.entity.StoreSettings;
import com.nocteon.nocteon_api.storeSettings.repository.StoreSettingsRepository;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StoreSettingsService {

    private final StoreSettingsRepository repository;

    public StoreSettingsResponse get() {
        StoreSettings settings = repository.getSettings();
        return StoreSettingsResponse.builder()
                .freeShippingThreshold(settings.getFreeShippingThreshold())
                .id(settings.getId())
                .updatedAt(settings.getUpdatedAt())
                .createdAt(settings.getCreatedAt()).build();
    }

    @Transactional
    public StoreSettingsResponse update(StoreSettingsRequest request) {
        StoreSettings settings = repository.getSettings();
        settings.setFreeShippingThreshold(request.getFreeShippingThreshold());
        repository.save(settings);
        return get();
    }
}