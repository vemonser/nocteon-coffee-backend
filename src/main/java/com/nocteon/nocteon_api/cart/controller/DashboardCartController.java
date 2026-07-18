package com.nocteon.nocteon_api.cart.controller;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import com.nocteon.nocteon_api.cart.dto.request.DashboardCartFilterRequest;
import com.nocteon.nocteon_api.cart.dto.response.DashboardCartResponse;
import com.nocteon.nocteon_api.cart.service.DashboardCartService;
import com.nocteon.nocteon_api.common.dto.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/dashboard/carts")
@Validated
@RequiredArgsConstructor
public class DashboardCartController {

    private final DashboardCartService dashboardCartService;

    @GetMapping
    @PreAuthorize("hasAuthority('cart:manage')")
    public ResponseEntity<ApiResponse<Page<DashboardCartResponse>>> getAll(
            @ModelAttribute DashboardCartFilterRequest filter) {
        return ResponseEntity.ok(ApiResponse.success(
                dashboardCartService.getAll(filter),
                "Carts retrieved successfully"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('cart:manage')")
    public ResponseEntity<ApiResponse<DashboardCartResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                dashboardCartService.getById(id),
                "Cart retrieved successfully"));
    }
}
