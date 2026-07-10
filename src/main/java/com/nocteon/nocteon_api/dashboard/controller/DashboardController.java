package com.nocteon.nocteon_api.dashboard.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nocteon.nocteon_api.common.dto.ApiResponse;
import com.nocteon.nocteon_api.dashboard.dto.DashboardOverviewResponse;
import com.nocteon.nocteon_api.dashboard.service.DashboardService;
import com.nocteon.nocteon_api.promoCode.dto.response.PromoCodeAnalyticsResponse;
import com.nocteon.nocteon_api.promoCode.service.PromoCodeAnalyticsService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final PromoCodeAnalyticsService promoCodeAnalyticsService;

    @GetMapping("/stats/overview")
    @PreAuthorize("hasAuthority('dashboard:read')")
    public ResponseEntity<ApiResponse<DashboardOverviewResponse>> getOverview(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getOverview(days), "Overview retrieved"));
    }

    @GetMapping("/promo-codes/analytics")
    @PreAuthorize("hasAuthority('promo:read')")
    public ResponseEntity<ApiResponse<PromoCodeAnalyticsResponse>> getAnalytics(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity
                .ok(ApiResponse.success(promoCodeAnalyticsService.getAnalytics(days), "Analytics retrieved"));
    }
}