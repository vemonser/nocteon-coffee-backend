package com.nocteon.nocteon_api.roastLevel.controller;

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
@Validated
@RequiredArgsConstructor
public class RoastLevelController {

    private final RoastLevelService roastLevelService;

    // RoastLevelController
    @GetMapping("/roast-levels")
    public ResponseEntity<ApiResponse<PageResponse<RoastLevelResponse>>> getAll(
            @Valid
            @ModelAttribute LookupFilterRequest filter) {
        return ResponseEntity.ok(
                ApiResponse.success(roastLevelService.getAll(filter), "Roast levels retrieved"));
    }

    @GetMapping("/dashboard/roast-levels")
    @PreAuthorize("hasAuthority('roast_level:read')")
    public ResponseEntity<ApiResponse<PageResponse<DashboardRoastLevelResponse>>> getAllDashboard(
            @Valid
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
    public ResponseEntity<ApiResponse<DashboardRoastLevelResponse>> create(
            @Valid @RequestBody RoastLevelRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(roastLevelService.create(request), "Roast level created"));
    }

    @PutMapping("/dashboard/roast-levels/{slug}")
    @PreAuthorize("hasAuthority('roast_level:update')")
    public ResponseEntity<ApiResponse<DashboardRoastLevelResponse>> update(
            @PathVariable String slug,
            @Valid @RequestBody RoastLevelRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(roastLevelService.update(slug, request), "Roast level updated"));
    }

    @DeleteMapping("/dashboard/roast-levels/{slug}")
    @PreAuthorize("hasAuthority('roast_level:delete')")
    public ResponseEntity<Void> delete(@PathVariable String slug) {
        roastLevelService.delete(slug);
        return ResponseEntity.noContent().build();
    }
    /**
     * One roast level by slug.
     *
     * <p>Previously {@code @GetMapping("/roast-levels/{slug}")} under the class-level {@code /api}, which made
     * this a catch-all over the entire public API namespace: {@code GET /api/anything} resolved to
     * a roast-level lookup rather than 404, and the roast-level detail page had no route at all.
     * Now it lives under the same prefix as the list endpoint, which is also what the
     * permitAll entry added for the storefront matches.
     */
    @GetMapping("/roast-levels/{slug}")
    public ResponseEntity<ApiResponse<RoastLevelResponse>> getBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.success(roastLevelService.getBySlug(slug), "Roast level retrieved"));
    }

        @GetMapping("/dashboard/roast-levels/deleted")
        @PreAuthorize("hasAuthority('roast_level:delete')")
        public ResponseEntity<ApiResponse<PageResponse<DashboardRoastLevelResponse>>> getAllDeleted(
                        @Valid
                        @ModelAttribute LookupFilterRequest filter) {
                return ResponseEntity.ok(
                                ApiResponse.success(
                                                roastLevelService.getAllDeleted(filter),
                                                "Deleted roast levels retrieved"));
        }

        @PatchMapping("/dashboard/roast-levels/{id}/restore")
        @PreAuthorize("hasAuthority('roast_level:delete')")
        public ResponseEntity<ApiResponse<Void>> restore(
                        @PathVariable Long id) {
                roastLevelService.restore(id);
                return ResponseEntity.ok(
                                ApiResponse.success(
                                                null,
                                                "Roast level restored"));
        }

        /**
         * Permanently deletes a trashed Roast level permanently deleted. The row must already be soft-deleted — restoring
         * is still possible until this is called, and after it nothing is recoverable. Cloudinary
         * assets are removed here and only here; the soft delete keeps them so a restore brings
         * the item back intact.
         */
        @DeleteMapping("/dashboard/roast-levels/{id}/permanent")
        @PreAuthorize("hasAuthority('roast_level:delete')")
        public ResponseEntity<Void> permanentDelete(@PathVariable Long id) {
                roastLevelService.permanentDelete(id);
                return ResponseEntity.noContent().build();
        }
}
