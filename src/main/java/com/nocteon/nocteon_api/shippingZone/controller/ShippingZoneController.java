package com.nocteon.nocteon_api.shippingZone.controller;

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
import org.springframework.web.bind.annotation.RestController;

import com.nocteon.nocteon_api.common.dto.ApiResponse;
import com.nocteon.nocteon_api.common.dto.BaseFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.shippingZone.dto.request.ShippingZoneRequest;
import com.nocteon.nocteon_api.shippingZone.dto.response.ShippingZoneResponse;
import com.nocteon.nocteon_api.shippingZone.service.ShippingZoneService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ShippingZoneController {

    private final ShippingZoneService shippingZoneService;

    @GetMapping("/dashboard/shipping-zones")
    @PreAuthorize("hasAuthority('shipping:read')")
    public ResponseEntity<ApiResponse<PageResponse<ShippingZoneResponse>>> getAll(
            @ModelAttribute BaseFilterRequest filter) {
        return ResponseEntity.ok(ApiResponse.success(shippingZoneService.getAll(filter), "Shipping zones retrieved"));
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
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        shippingZoneService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Shipping zone deleted"));
    }
}