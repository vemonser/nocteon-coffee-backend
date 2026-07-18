package com.nocteon.nocteon_api.brewingMethod.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import com.nocteon.nocteon_api.brewingMethod.dto.request.BrewingMethodRequest;
import com.nocteon.nocteon_api.brewingMethod.dto.response.BrewingMethodResponse;
import com.nocteon.nocteon_api.brewingMethod.dto.response.BrewingMethodResponseDashboard;
import com.nocteon.nocteon_api.brewingMethod.service.BrewingMethodService;
import com.nocteon.nocteon_api.common.dto.ApiResponse;
import com.nocteon.nocteon_api.common.dto.LookupFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductWithScoreResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@Validated
@RequiredArgsConstructor
public class BrewingMethodController {

    private final BrewingMethodService brewingMethodService;

    // Public
    @GetMapping("/brewing-methods")
    public ResponseEntity<ApiResponse<PageResponse<BrewingMethodResponse>>> getAll(
            @ModelAttribute LookupFilterRequest filter) {
        return ResponseEntity.ok(
                ApiResponse.success(brewingMethodService.getAll(filter), "Brewing method retrieved"));
    }

    @GetMapping("/brewing-methods/{slug}")
    public ResponseEntity<ApiResponse<BrewingMethodResponse>> getBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.success(brewingMethodService.getBySlug(slug), "Brewing method retrieved"));
    }

    @GetMapping("/dashboard/brewing-methods/{slug}")
    @PreAuthorize("hasAuthority('brewing_method:read')")
    public ResponseEntity<ApiResponse<BrewingMethodResponseDashboard>> getDashboardBySlug(
            @PathVariable String slug) {
        return ResponseEntity.ok(
                ApiResponse.success(brewingMethodService.getDashboardBySlug(slug), "Brewing method retrieved"));
    }

    // ===== Dashboard Endpoints =====
    @GetMapping("/dashboard/brewing-methods/{slug}/products")
    @PreAuthorize("hasAuthority('brewing_method:read')")
    public ResponseEntity<ApiResponse<PageResponse<ProductWithScoreResponse>>> getProductsByBrewingMethod(
            @PathVariable String slug,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        brewingMethodService.getProductsByBrewingMethod(slug, page, size, sort, direction),
                        "Linked products retrieved"));
    }

    @GetMapping("/dashboard/brewing-methods")
    @PreAuthorize("hasAuthority('brewing_method:read')")
    public ResponseEntity<ApiResponse<PageResponse<BrewingMethodResponseDashboard>>> getAllDashboard(
            @ModelAttribute LookupFilterRequest filter) {
        return ResponseEntity.ok(
                ApiResponse.success(brewingMethodService.getAllDashboard(filter), "Brewing method retrieved"));
    }

    @PostMapping("/dashboard/brewing-methods")
    @PreAuthorize("hasAuthority('brewing_method:create')")
    public ResponseEntity<ApiResponse<BrewingMethodResponse>> create(
            @Valid @RequestBody BrewingMethodRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(brewingMethodService.create(request), "Brewing method created"));
    }

    @PutMapping("/dashboard/brewing-methods/{slug}")
    @PreAuthorize("hasAuthority('brewing_method:update')")
    public ResponseEntity<ApiResponse<BrewingMethodResponse>> update(
            @PathVariable String slug,
            @Valid @RequestBody BrewingMethodRequest request) {
        return ResponseEntity
                .ok(ApiResponse.success(brewingMethodService.update(slug, request), "Brewing method updated"));
    }

    @DeleteMapping("/dashboard/brewing-methods/{slug}")
    @PreAuthorize("hasAuthority('brewing_method:delete')")
    public ResponseEntity<Void> delete(@PathVariable String slug) {
        brewingMethodService.delete(slug);
        return ResponseEntity.noContent().build();
    }
}