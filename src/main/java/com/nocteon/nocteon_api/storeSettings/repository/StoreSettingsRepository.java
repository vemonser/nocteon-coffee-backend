package com.nocteon.nocteon_api.storeSettings.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nocteon.nocteon_api.storeSettings.entity.StoreSettings;

public interface StoreSettingsRepository extends JpaRepository<StoreSettings, Long> {

    default StoreSettings getSettings() {
        return findById(1L).orElseThrow(() -> new IllegalStateException("Store settings row missing"));
    }
}