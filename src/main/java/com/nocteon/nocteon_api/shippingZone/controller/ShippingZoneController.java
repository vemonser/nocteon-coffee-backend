package com.nocteon.nocteon_api.shippingZone.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import com.nocteon.nocteon_api.common.dto.ApiResponse;
import com.nocteon.nocteon_api.common.dto.BaseFilterRequest;
import com.nocteon.nocteon_api.common.dto.LookupFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.shippingZone.dto.request.ShippingZoneRequest;
import com.nocteon.nocteon_api.shippingZone.dto.response.ShippingZoneResponse;
import com.nocteon.nocteon_api.shippingZone.service.ShippingZoneService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@Validated
@RequiredArgsConstructor
public class ShippingZoneController {

    private final ShippingZoneService shippingZoneService;

    @GetMapping("/dashboard/shipping-zones")
    @PreAuthorize("hasAuthority('shipping:read')")
    public ResponseEntity<ApiResponse<PageResponse<ShippingZoneResponse>>> getAll(
            @Valid
            @ModelAttribute BaseFilterRequest filter) {
        return ResponseEntity.ok(ApiResponse.success(shippingZoneService.getAll(filter), "Shipping zones retrieved"));
    }

    @GetMapping("/dashboard/shipping-zones/{id}")
    @PreAuthorize("hasAuthority('shipping:read')")
    public ResponseEntity<ApiResponse<ShippingZoneResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(shippingZoneService.getById(id), "Shipping zone retrieved"));
    }

    @PostMapping("/dashboard/shipping-zones")
    @PreAuthorize("hasAuthority('shipping:create')")
    public ResponseEntity<ApiResponse<ShippingZoneResponse>> create(@RequestBody @Valid ShippingZoneRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(shippingZoneService.create(request), "Shipping zone created"));
    }

    @PutMapping("/dashboard/shipping-zones/{id}")
    @PreAuthorize("hasAuthority('shipping:update')")
    public ResponseEntity<ApiResponse<ShippingZoneResponse>> update(
            @PathVariable Long id, @RequestBody @Valid ShippingZoneRequest request) {
        return ResponseEntity.ok(ApiResponse.success(shippingZoneService.update(id, request), "Shipping zone updated"));
    }

    @DeleteMapping("/dashboard/shipping-zones/{id}")
    @PreAuthorize("hasAuthority('shipping:delete')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        shippingZoneService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/dashboard/shipping-zones/deleted")
    @PreAuthorize("hasAuthority('shipping:delete')")
    public ResponseEntity<ApiResponse<PageResponse<ShippingZoneResponse>>> getAllDeleted(
            @Valid
            @ModelAttribute LookupFilterRequest filter) {
        return ResponseEntity.ok(
                ApiResponse.success(shippingZoneService.getAllDeleted(filter), "Deleted shipping zones retrieved"));
    }

    @PatchMapping("/dashboard/shipping-zones/{id}/restore")
    @PreAuthorize("hasAuthority('shipping:delete')")
    public ResponseEntity<ApiResponse<Void>> restore(@PathVariable Long id) {
        shippingZoneService.restore(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Shipping zone restored"));
    }

        /**
         * Permanently deletes a trashed Shipping zone permanently deleted. The row must already be soft-deleted — restoring
         * is still possible until this is called, and after it nothing is recoverable. Cloudinary
         * assets are removed here and only here; the soft delete keeps them so a restore brings
         * the item back intact.
         */
        @DeleteMapping("/dashboard/shipping-zones/{id}/permanent")
        @PreAuthorize("hasAuthority('shipping:delete')")
        public ResponseEntity<Void> permanentDelete(@PathVariable Long id) {
                shippingZoneService.permanentDelete(id);
                return ResponseEntity.noContent().build();
        }
}
