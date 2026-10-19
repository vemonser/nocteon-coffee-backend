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
                .currency(settings.getCurrency())
                .id(settings.getId())
                .isFreeShippingActive(settings.getIsFreeShippingActive() == null ? Boolean.TRUE : settings.getIsFreeShippingActive())
                .updatedAt(settings.getUpdatedAt())
                .createdAt(settings.getCreatedAt()).build();
    }

    @Transactional
    public StoreSettingsResponse update(StoreSettingsRequest request) {
        StoreSettings settings = repository.getSettings();
        settings.setFreeShippingThreshold(request.getFreeShippingThreshold());
        if (request.getCurrency() != null) {
            settings.setCurrency(request.getCurrency());
        }
        if (request.getIsFreeShippingActive() != null) {
            settings.setIsFreeShippingActive(request.getIsFreeShippingActive());
        }
        repository.save(settings);
        return get();
    }
}