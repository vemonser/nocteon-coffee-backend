package com.nocteon.nocteon_api.cart.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import com.nocteon.nocteon_api.cart.dto.request.DashboardCartFilterRequest;
import com.nocteon.nocteon_api.cart.dto.response.CartStatsResponse;
import com.nocteon.nocteon_api.cart.dto.response.DashboardCartResponse;
import com.nocteon.nocteon_api.cart.service.DashboardCartService;
import com.nocteon.nocteon_api.common.dto.ApiResponse;
import com.nocteon.nocteon_api.common.dto.PageResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/dashboard/carts")
@Validated
@RequiredArgsConstructor
public class DashboardCartController {

    private final DashboardCartService dashboardCartService;

    @GetMapping
    @PreAuthorize("hasAuthority('cart:read_all')")
    public ResponseEntity<ApiResponse<PageResponse<DashboardCartResponse>>> getAll(
            @Valid
            @ModelAttribute DashboardCartFilterRequest filter) {
        return ResponseEntity.ok(ApiResponse.success(
                PageResponse.of(dashboardCartService.getAll(filter)),
                "Carts retrieved successfully"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('cart:read_all')")
    public ResponseEntity<ApiResponse<DashboardCartResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                dashboardCartService.getById(id),
                "Cart retrieved successfully"));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('cart:read_all')")
    public ResponseEntity<ApiResponse<CartStatsResponse>> getStats() {
        return ResponseEntity.ok(ApiResponse.success(
                dashboardCartService.getStats(),
                "Cart stats retrieved successfully"));
    }

    @PostMapping("/{id}/remind")
    @PreAuthorize("hasAuthority('cart:notify')")
    public ResponseEntity<ApiResponse<Boolean>> sendReminder(@PathVariable Long id) {
        boolean sent = dashboardCartService.sendReminder(id);
        return ResponseEntity.ok(ApiResponse.success(
                sent,
                sent ? "Reminder queued" : "Cart not found or empty"));
    }
}