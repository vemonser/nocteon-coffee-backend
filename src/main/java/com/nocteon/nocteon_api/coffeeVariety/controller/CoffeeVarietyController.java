package com.nocteon.nocteon_api.coffeeVariety.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import com.nocteon.nocteon_api.coffeeVariety.dto.request.CoffeeVarietyRequest;
import com.nocteon.nocteon_api.coffeeVariety.dto.response.CoffeeVarietyResponse;
import com.nocteon.nocteon_api.coffeeVariety.dto.response.CoffeeVarietyResponseDashboard;
import com.nocteon.nocteon_api.coffeeVariety.service.CoffeeVarietyService;
import com.nocteon.nocteon_api.common.dto.ApiResponse;
import com.nocteon.nocteon_api.common.dto.LookupFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@Validated
@RequiredArgsConstructor
public class CoffeeVarietyController {

    private final CoffeeVarietyService coffeeVarietyService;

    @GetMapping("/coffee-varieties")
    public ResponseEntity<ApiResponse<PageResponse<CoffeeVarietyResponse>>> getAll(
        @Valid
        @ModelAttribute LookupFilterRequest filter

    ) {
        return ResponseEntity.ok(ApiResponse.success(coffeeVarietyService.getAll(filter), "Coffee varieties retrieved"));
    }

    @GetMapping("/coffee-varieties/{slug}")
    public ResponseEntity<ApiResponse<CoffeeVarietyResponse>> getBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.success(coffeeVarietyService.getBySlug(slug), "Coffee variety retrieved"));
    }

   // ===== Dashboard Endpoints =====

    @GetMapping("/dashboard/coffee-varieties")
    @PreAuthorize("hasAuthority('coffee_variety:read')")
    public ResponseEntity<ApiResponse<PageResponse<CoffeeVarietyResponseDashboard>>> getAllDashboard(
            @Valid
            @ModelAttribute LookupFilterRequest filter) {
        return ResponseEntity.ok(
                ApiResponse.success(coffeeVarietyService.getAllDashboard(filter), "Brewing method retrieved"));
    }

    @GetMapping("/dashboard/coffee-varieties/{slug}")
    @PreAuthorize("hasAuthority('coffee_variety:read')")
    public ResponseEntity<ApiResponse<CoffeeVarietyResponseDashboard>> getDashboardBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(
                ApiResponse.success(coffeeVarietyService.getDashboardBySlug(slug), "Coffee variety retrieved"));
    }


    @PostMapping("/dashboard/coffee-varieties")
    @PreAuthorize("hasAuthority('coffee_variety:create')")
    public ResponseEntity<ApiResponse<CoffeeVarietyResponseDashboard>> create(
            @Valid @RequestBody CoffeeVarietyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(coffeeVarietyService.create(request), "Coffee variety created"));
    }

    @PutMapping("/dashboard/coffee-varieties/{slug}")
    @PreAuthorize("hasAuthority('coffee_variety:update')")
    public ResponseEntity<ApiResponse<CoffeeVarietyResponseDashboard>> update(
            @PathVariable String slug,
            @Valid @RequestBody CoffeeVarietyRequest request) {
        return ResponseEntity.ok(ApiResponse.success(coffeeVarietyService.update(slug, request), "Coffee variety updated"));
    }

    @DeleteMapping("/dashboard/coffee-varieties/{slug}")
    @PreAuthorize("hasAuthority('coffee_variety:delete')")
    public ResponseEntity<Void> delete(@PathVariable String slug) {
        coffeeVarietyService.delete(slug);
        return ResponseEntity.noContent().build();
    }

        @GetMapping("/dashboard/coffee-varieties/deleted")
        @PreAuthorize("hasAuthority('coffee_variety:delete')")
        public ResponseEntity<ApiResponse<PageResponse<CoffeeVarietyResponseDashboard>>> getAllDeleted(
                        @Valid
                        @ModelAttribute LookupFilterRequest filter) {
                return ResponseEntity.ok(
                                ApiResponse.success(
                                                coffeeVarietyService.getAllDeleted(filter),
                                                "Deleted coffee varieties retrieved"));
        }

        @PatchMapping("/dashboard/coffee-varieties/{id}/restore")
        @PreAuthorize("hasAuthority('coffee_variety:delete')")
        public ResponseEntity<ApiResponse<Void>> restore(
                        @PathVariable Long id) {
                coffeeVarietyService.restore(id);
                return ResponseEntity.ok(
                                ApiResponse.success(
                                                null,
                                                "Coffee variety restored"));
        }

        /**
         * Permanently deletes a trashed Coffee variety permanently deleted. The row must already be soft-deleted — restoring
         * is still possible until this is called, and after it nothing is recoverable. Cloudinary
         * assets are removed here and only here; the soft delete keeps them so a restore brings
         * the item back intact.
         */
        @DeleteMapping("/dashboard/coffee-varieties/{id}/permanent")
        @PreAuthorize("hasAuthority('coffee_variety:delete')")
        public ResponseEntity<Void> permanentDelete(@PathVariable Long id) {
                coffeeVarietyService.permanentDelete(id);
                return ResponseEntity.noContent().build();
        }
}
