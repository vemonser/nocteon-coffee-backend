package com.nocteon.nocteon_api.product.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;

import com.nocteon.nocteon_api.common.dto.ApiResponse;
import com.nocteon.nocteon_api.common.dto.LookupFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.product.dto.request.ProductOptionRequest;
import com.nocteon.nocteon_api.product.dto.request.ProductOptionValueRequest;
import com.nocteon.nocteon_api.product.dto.request.ProductOptionValuePatchRequest;
import com.nocteon.nocteon_api.product.dto.response.ProductOptionResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductOptionResponseDashboard;
import com.nocteon.nocteon_api.product.dto.response.ProductOptionValueResponseDashboard;
import com.nocteon.nocteon_api.product.service.ProductOptionService;
import com.nocteon.nocteon_api.product.enums.ProductType;

import io.swagger.v3.oas.annotations.parameters.RequestBody;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@Validated
@RequiredArgsConstructor
public class ProductOptionController {

    private final ProductOptionService productOptionService;

    @GetMapping("/dashboard/product-options")
    @PreAuthorize("hasAuthority('product:read')")
    public ResponseEntity<ApiResponse<PageResponse<ProductOptionResponseDashboard>>> getAllDashboard(
            @Valid
            @ModelAttribute LookupFilterRequest filter,
            @RequestParam(required = false) ProductType productType) {
        return ResponseEntity.ok(
                ApiResponse.success(productOptionService.getAllDashboard(filter, productType), "Options retrieved"));
    }

    @GetMapping("/dashboard/product-options/{optionId}")
    @PreAuthorize("hasAuthority('product:read')")
    public ResponseEntity<ApiResponse<ProductOptionResponseDashboard>> getDashboardById(
            @PathVariable Long optionId) {
        return ResponseEntity.ok(
                ApiResponse.success(productOptionService.getDashboardById(optionId), "Option retrieved"));
    }

    @PostMapping("/dashboard/product-options")
    @PreAuthorize("hasAuthority('product:update')")
    public ResponseEntity<ApiResponse<ProductOptionResponse>> create(
            @Valid @RequestBody ProductOptionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(productOptionService.create(request), "Option created"));
    }

    @PutMapping("/dashboard/product-options/{optionId}")
    @PreAuthorize("hasAuthority('product:update')")
    public ResponseEntity<ApiResponse<ProductOptionResponse>> update(
            @PathVariable Long optionId,
            @Valid @RequestBody ProductOptionRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(productOptionService.update(optionId, request), "Option updated"));
    }

    @DeleteMapping("/dashboard/product-options/{optionId}")
    @PreAuthorize("hasAuthority('product:update')")
    public ResponseEntity<Void> delete(@PathVariable Long optionId) {
        productOptionService.delete(optionId);
        return ResponseEntity.noContent().build();
    }

    // ===== Values =====

    @PostMapping("/dashboard/product-options/{optionId}/values")
    @PreAuthorize("hasAuthority('product:update')")
    public ResponseEntity<ApiResponse<ProductOptionValueResponseDashboard>> addValue(
            @PathVariable Long optionId,
            @Valid @RequestBody ProductOptionValueRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(productOptionService.addValue(optionId, request), "Value added"));
    }

    @DeleteMapping("/dashboard/product-options/{optionId}/values/{valueId}")
    @PreAuthorize("hasAuthority('product:update')")
    public ResponseEntity<Void> deleteValue(
            @PathVariable Long optionId,
            @PathVariable Long valueId) {
        productOptionService.deleteValue(optionId, valueId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/dashboard/product-options/{optionId}/values/{valueId}")
    @PreAuthorize("hasAuthority('product:update')")
    public ResponseEntity<ApiResponse<ProductOptionValueResponseDashboard>> patchValue(
            @PathVariable Long optionId,
            @PathVariable Long valueId,
            @Valid @RequestBody ProductOptionValuePatchRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(productOptionService.patchValue(optionId, valueId, request), "Value updated"));
    }

    @GetMapping("/dashboard/product-options/{optionId}/values/{valueId}/usage")
    @PreAuthorize("hasAuthority('product:read')")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getValueUsage(
            @PathVariable Long optionId,
            @PathVariable Long valueId) {
        long count = productOptionService.getValueUsageCount(optionId, valueId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("count", count), "Usage retrieved"));
    }

    @PutMapping("/dashboard/product-options/{optionId}/values/order")
    @PreAuthorize("hasAuthority('product:update')")
    public ResponseEntity<Void> reorderValues(
            @PathVariable Long optionId,
            @Valid @RequestBody List<Long> valueIds) {
        productOptionService.reorderValues(optionId, valueIds);
        return ResponseEntity.noContent().build();
    }

    // ===== Linking to products =====

    @PostMapping("/dashboard/products/{productId}/options/{optionId}")
    @PreAuthorize("hasAuthority('product:update')")
    public ResponseEntity<Void> linkToProduct(
            @PathVariable Long productId,
            @PathVariable Long optionId) {
        productOptionService.linkToProduct(productId, optionId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/dashboard/products/{productId}/options/{optionId}")
    @PreAuthorize("hasAuthority('product:update')")
    public ResponseEntity<Void> unlinkFromProduct(
            @PathVariable Long productId,
            @PathVariable Long optionId) {
        productOptionService.unlinkFromProduct(productId, optionId);
        return ResponseEntity.noContent().build();
    }
}