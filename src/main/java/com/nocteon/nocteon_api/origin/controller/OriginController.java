package com.nocteon.nocteon_api.origin.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;

import com.nocteon.nocteon_api.common.dto.ApiResponse;
import com.nocteon.nocteon_api.common.dto.LookupFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.origin.dto.request.OriginRequest;
import com.nocteon.nocteon_api.origin.dto.response.DashboardOriginResponse;
import com.nocteon.nocteon_api.origin.dto.response.OriginOptionResponse;
import com.nocteon.nocteon_api.origin.dto.response.OriginResponse;
import com.nocteon.nocteon_api.origin.service.OriginService;
import com.nocteon.nocteon_api.product.dto.response.ProductCardResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@Validated
@RequiredArgsConstructor
public class OriginController {

        private final OriginService originService;

        // Public
        @GetMapping("/origins")
        public ResponseEntity<ApiResponse<PageResponse<OriginResponse>>> getAll(
                        @Valid
                        @ModelAttribute LookupFilterRequest filter) {
                return ResponseEntity.ok(
                                ApiResponse.success(originService.getAll(filter), "Origins retrieved"));
        }

        @GetMapping("/origins/{slug}")
        public ResponseEntity<ApiResponse<OriginResponse>> getOrigin(@PathVariable String slug) {
                return ResponseEntity.ok(ApiResponse.success(originService.getOrigin(slug), "Origin retrieved"));
        }

        @GetMapping("/origins/{slug}/products")
        public ResponseEntity<ApiResponse<PageResponse<ProductCardResponse>>> getOriginProducts(
                        @PathVariable String slug,
                        @Valid
                        @ModelAttribute LookupFilterRequest filter) {
                Page<ProductCardResponse> page = originService.getProductsByOriginSlug(slug, filter.toPageable());
                return ResponseEntity.ok(ApiResponse.success(PageResponse.of(page), "Products retrieved"));
        }

        // Dashboard
        @GetMapping("/dashboard/origins")
        @PreAuthorize("hasAuthority('origin:read')")
        public ResponseEntity<ApiResponse<PageResponse<DashboardOriginResponse>>> getAllDashboard(
                        @Valid
                        @ModelAttribute LookupFilterRequest filter) {
                return ResponseEntity.ok(
                                ApiResponse.success(originService.getAllDashboard(filter), "Origins retrieved"));
        }

        @GetMapping("/dashboard/origins/options")
        @PreAuthorize("hasAuthority('origin:read')")
        public ResponseEntity<ApiResponse<List<OriginOptionResponse>>> getOptions() {
                return ResponseEntity.ok(
                                ApiResponse.success(originService.getOptions(), "Origin options retrieved"));
        }

        @GetMapping("/dashboard/origins/{slug}")
        @PreAuthorize("hasAuthority('origin:read')")
        public ResponseEntity<ApiResponse<DashboardOriginResponse>> getDashboardBySlug(@PathVariable String slug) {
                return ResponseEntity.ok(
                                ApiResponse.success(originService.getDashboardBySlug(slug), "origin retrieved"));
        }

        @PostMapping(value = "/dashboard/origins", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @PreAuthorize("hasAuthority('origin:create')")
        public ResponseEntity<ApiResponse<DashboardOriginResponse>> createOrigin(
                        @Valid @RequestPart("data") OriginRequest request,
                        @RequestPart(value = "image", required = false) MultipartFile image) {
                return ResponseEntity.status(HttpStatus.CREATED)
                                .body(ApiResponse.success(
                                                originService.create(request, image),
                                                "Origin created"));
        }

        @PutMapping(value = "/dashboard/origins/{slug}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @PreAuthorize("hasAuthority('origin:update')")
        public ResponseEntity<ApiResponse<DashboardOriginResponse>> updateOrigin(
                        @PathVariable String slug,
                        @Valid @RequestPart("data") OriginRequest request,
                        @RequestPart(value = "image", required = false) MultipartFile image) {
                return ResponseEntity.ok(
                                ApiResponse.success(
                                                originService.update(slug, request, image),
                                                "Origin updated"));
        }

        @PostMapping("/dashboard/origins/{slug}/image")
        @PreAuthorize("hasAuthority('origin:update')")
        public ResponseEntity<ApiResponse<DashboardOriginResponse>> uploadOriginImage(
                        @PathVariable String slug,
                        @RequestParam("file") MultipartFile file) {
                return ResponseEntity.ok(
                                ApiResponse.success(
                                                originService.uploadImage(slug, file),
                                                "Image uploaded successfully"));
        }

        @DeleteMapping("/dashboard/origins/{slug}")
        @PreAuthorize("hasAuthority('origin:delete')")
        public ResponseEntity<Void> deleteOrigin(
                        @PathVariable String slug) {
                originService.delete(slug);
                return ResponseEntity.noContent().build();
        }

        @GetMapping("/dashboard/origins/deleted")
        @PreAuthorize("hasAuthority('origin:delete')")
        public ResponseEntity<ApiResponse<PageResponse<DashboardOriginResponse>>> getAllDeleted(
                        @Valid
                        @ModelAttribute LookupFilterRequest filter) {
                return ResponseEntity.ok(
                                ApiResponse.success(
                                                originService.getAllDeleted(filter),
                                                "Deleted origins retrieved"));
        }

        @PatchMapping("/dashboard/origins/{id}/restore")
        @PreAuthorize("hasAuthority('origin:delete')")
        public ResponseEntity<ApiResponse<Void>> restore(
                        @PathVariable Long id) {
                originService.restore(id);
                return ResponseEntity.ok(
                                ApiResponse.success(
                                                null,
                                                "Origin restored"));
        }

        /**
         * Permanently deletes a trashed Origin permanently deleted. The row must already be soft-deleted — restoring
         * is still possible until this is called, and after it nothing is recoverable. Cloudinary
         * assets are removed here and only here; the soft delete keeps them so a restore brings
         * the item back intact.
         */
        @DeleteMapping("/dashboard/origins/{id}/permanent")
        @PreAuthorize("hasAuthority('origin:delete')")
        public ResponseEntity<Void> permanentDelete(@PathVariable Long id) {
                originService.permanentDelete(id);
                return ResponseEntity.noContent().build();
        }
}
