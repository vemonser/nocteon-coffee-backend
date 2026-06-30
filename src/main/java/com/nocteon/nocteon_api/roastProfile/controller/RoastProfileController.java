package com.nocteon.nocteon_api.roastProfile.controller;

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
import com.nocteon.nocteon_api.common.dto.LookupFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.roastProfile.dto.request.RoastProfileRequest;
import com.nocteon.nocteon_api.roastProfile.dto.response.DashboardRoastProfileResponse;
import com.nocteon.nocteon_api.roastProfile.dto.response.RoastProfileResponse;
import com.nocteon.nocteon_api.roastProfile.service.RoastProfileService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RoastProfileController {

    private final RoastProfileService roastProfileService;

    // RoastProfileController
    @GetMapping("/roast-profiles")
    public ResponseEntity<ApiResponse<PageResponse<RoastProfileResponse>>> getAll(
            @ModelAttribute LookupFilterRequest filter) {
        return ResponseEntity.ok(
                ApiResponse.success(roastProfileService.getAll(filter), "Roast profiles retrieved"));
    }

    @GetMapping("/dashboard/roast-profiles")
    @PreAuthorize("hasAuthority('roast_profile:read')")
    public ResponseEntity<ApiResponse<PageResponse<DashboardRoastProfileResponse>>> getAllDashboard(
            @ModelAttribute LookupFilterRequest filter) {
        return ResponseEntity.ok(
                ApiResponse.success(roastProfileService.getAllDashboard(filter), "Roast profiles retrieved"));
    }

    @PostMapping("/dashboard/roast-profiles")
    @PreAuthorize("hasAuthority('roast_profile:create')")
    public ResponseEntity<ApiResponse<RoastProfileResponse>> create(
            @Valid @RequestBody RoastProfileRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(roastProfileService.create(request), "Roast profile created"));
    }

    @PutMapping("/dashboard/roast-profiles/{slug}")
    @PreAuthorize("hasAuthority('roast_profile:update')")
    public ResponseEntity<ApiResponse<RoastProfileResponse>> update(
            @PathVariable String slug,
            @Valid @RequestBody RoastProfileRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(roastProfileService.update(slug, request), "Roast profile updated"));
    }

    @DeleteMapping("/dashboard/roast-profiles/{slug}")
    @PreAuthorize("hasAuthority('roast_profile:delete')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String slug) {
        roastProfileService.delete(slug);
        return ResponseEntity.ok(ApiResponse.success(null, "Roast profile deleted"));
    }
    @GetMapping("/{slug}")
    public ResponseEntity<ApiResponse<RoastProfileResponse>> getBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.success(roastProfileService.getBySlug(slug), "Roast profile retrieved"));
    }
}