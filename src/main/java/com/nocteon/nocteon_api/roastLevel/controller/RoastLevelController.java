package com.nocteon.nocteon_api.roastLevel.controller;

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
import com.nocteon.nocteon_api.roastLevel.dto.request.RoastLevelRequest;
import com.nocteon.nocteon_api.roastLevel.dto.response.DashboardRoastLevelResponse;
import com.nocteon.nocteon_api.roastLevel.dto.response.RoastLevelResponse;
import com.nocteon.nocteon_api.roastLevel.service.RoastLevelService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RoastLevelController {

    private final RoastLevelService roastLevelService;

    // RoastLevelController
    @GetMapping("/roast-levels")
    public ResponseEntity<ApiResponse<PageResponse<RoastLevelResponse>>> getAll(
            @ModelAttribute LookupFilterRequest filter) {
        return ResponseEntity.ok(
                ApiResponse.success(roastLevelService.getAll(filter), "Roast levels retrieved"));
    }

    @GetMapping("/dashboard/roast-levels")
    @PreAuthorize("hasAuthority('roast_level:read')")
    public ResponseEntity<ApiResponse<PageResponse<DashboardRoastLevelResponse>>> getAllDashboard(
            @ModelAttribute LookupFilterRequest filter) {
        return ResponseEntity.ok(
                ApiResponse.success(roastLevelService.getAllDashboard(filter), "Roast levels retrieved"));
    }

    @GetMapping("/dashboard/roast-levels/{slug}")
    @PreAuthorize("hasAuthority('roast_level:read')")
    public ResponseEntity<ApiResponse<DashboardRoastLevelResponse>> getDashboardBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(
                ApiResponse.success(roastLevelService.getDashboardBySlug(slug), "Roast level retrieved"));
    }

    @PostMapping("/dashboard/roast-levels")
    @PreAuthorize("hasAuthority('roast_level:create')")
    public ResponseEntity<ApiResponse<RoastLevelResponse>> create(
            @Valid @RequestBody RoastLevelRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(roastLevelService.create(request), "Roast level created"));
    }

    @PutMapping("/dashboard/roast-levels/{slug}")
    @PreAuthorize("hasAuthority('roast_level:update')")
    public ResponseEntity<ApiResponse<RoastLevelResponse>> update(
            @PathVariable String slug,
            @Valid @RequestBody RoastLevelRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(roastLevelService.update(slug, request), "Roast level updated"));
    }

    @DeleteMapping("/dashboard/roast-levels/{slug}")
    @PreAuthorize("hasAuthority('roast_level:delete')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String slug) {
        roastLevelService.delete(slug);
        return ResponseEntity.ok(ApiResponse.success(null, "Roast level deleted"));
    }
    @GetMapping("/{slug}")
    public ResponseEntity<ApiResponse<RoastLevelResponse>> getBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.success(roastLevelService.getBySlug(slug), "Roast level retrieved"));
    }
}