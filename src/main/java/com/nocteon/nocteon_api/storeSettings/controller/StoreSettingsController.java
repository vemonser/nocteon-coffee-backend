package com.nocteon.nocteon_api.storeSettings.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nocteon.nocteon_api.common.dto.ApiResponse;
import com.nocteon.nocteon_api.storeSettings.dto.request.StoreSettingsRequest;
import com.nocteon.nocteon_api.storeSettings.dto.response.StoreSettingsResponse;
import com.nocteon.nocteon_api.storeSettings.service.StoreSettingsService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class StoreSettingsController {

    private final StoreSettingsService storeSettingsService;

    @GetMapping("/settings")
    public ResponseEntity<ApiResponse<StoreSettingsResponse>> get() {
        return ResponseEntity.ok(ApiResponse.success(storeSettingsService.get(), "Settings retrieved"));
    }

    @PutMapping("/dashboard/settings")
    @PreAuthorize("hasAuthority('settings:update')")
    public ResponseEntity<ApiResponse<StoreSettingsResponse>> update(@Valid @RequestBody StoreSettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.success(storeSettingsService.update(request), "Settings updated"));
    }
}