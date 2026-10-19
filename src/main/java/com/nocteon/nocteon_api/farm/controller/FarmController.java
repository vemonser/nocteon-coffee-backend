package com.nocteon.nocteon_api.farm.controller;


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
import com.nocteon.nocteon_api.farm.dto.request.FarmFilterRequest;
import com.nocteon.nocteon_api.farm.dto.request.FarmRequest;
import com.nocteon.nocteon_api.farm.dto.response.DashboardFarmResponse;
import com.nocteon.nocteon_api.farm.dto.response.FarmResponse;
import com.nocteon.nocteon_api.farm.service.FarmService;
import com.nocteon.nocteon_api.product.dto.response.ProductCardResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@Validated
@RequiredArgsConstructor
public class FarmController {

        private final FarmService farmService;

        @GetMapping("/farms")
        public ResponseEntity<ApiResponse<PageResponse<FarmResponse>>> getAll(
                        @Valid
                        @ModelAttribute FarmFilterRequest filter) {
                return ResponseEntity.ok(
                                ApiResponse.success(farmService.getAll(filter), "Farms retrieved"));
        }

        @GetMapping("/farms/{slug}")
        public ResponseEntity<ApiResponse<FarmResponse>> getBySlug(
                        @PathVariable String slug) {
                return ResponseEntity.ok(
                                ApiResponse.success(farmService.getBySlug(slug), "Farm retrieved"));
        }

        @GetMapping("/farms/{slug}/products")
        public ResponseEntity<ApiResponse<PageResponse<ProductCardResponse>>> getFarmProducts(
                        @PathVariable String slug,
                        @Valid
                        @ModelAttribute LookupFilterRequest filter) {
                Page<ProductCardResponse> page = farmService.getProductsByFarmSlug(slug, filter.toPageable());
                return ResponseEntity.ok(ApiResponse.success(PageResponse.of(page), "Products retrieved"));
        }

        @GetMapping("/dashboard/farms")
        @PreAuthorize("hasAuthority('farm:read')")
        public ResponseEntity<ApiResponse<PageResponse<DashboardFarmResponse>>> getAllDashboard(
                        @Valid
                        @ModelAttribute FarmFilterRequest filter) {
                return ResponseEntity.ok(
                                ApiResponse.success(farmService.getAllDashboard(filter), "Farms retrieved"));
        }

        @GetMapping("/dashboard/farms/{slug}")
        @PreAuthorize("hasAuthority('farm:read')")
        public ResponseEntity<ApiResponse<DashboardFarmResponse>> getDashboardBySlug(@PathVariable String slug) {
                return ResponseEntity.ok(
                                ApiResponse.success(farmService.getDashboardBySlug(slug), "Farm retrieved"));
        }

        @PostMapping(value = "/dashboard/farms", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @PreAuthorize("hasAuthority('farm:create')")
        public ResponseEntity<ApiResponse<DashboardFarmResponse>> create(
                        @Valid @RequestPart("data") FarmRequest request,
                        @RequestPart(value = "image", required = false) MultipartFile image) {
                return ResponseEntity.status(HttpStatus.CREATED)
                                .body(ApiResponse.success(farmService.create(request, image), "Farm created"));
        }

        @PutMapping(value = "/dashboard/farms/{slug}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @PreAuthorize("hasAuthority('farm:update')")
        public ResponseEntity<ApiResponse<DashboardFarmResponse>> update(
                        @PathVariable String slug,
                        @Valid @RequestPart("data") FarmRequest request,
                        @RequestPart(value = "image", required = false) MultipartFile image) {
                return ResponseEntity.ok(
                                ApiResponse.success(farmService.update(slug, request, image), "Farm updated"));
        }

        @PostMapping("/dashboard/farms/{slug}/image")
        @PreAuthorize("hasAuthority('farm:update')")
        public ResponseEntity<ApiResponse<DashboardFarmResponse>> uploadImage(
                        @PathVariable String slug,
                        @RequestParam("file") MultipartFile file) {
                return ResponseEntity.ok(
                                ApiResponse.success(farmService.uploadImage(slug, file), "Image uploaded"));
        }

        @DeleteMapping("/dashboard/farms/{slug}")
        @PreAuthorize("hasAuthority('farm:delete')")
        public ResponseEntity<Void> delete(@PathVariable String slug) {
                farmService.delete(slug);
                return ResponseEntity.noContent().build();
        }

        @GetMapping("/dashboard/farms/deleted")
        @PreAuthorize("hasAuthority('farm:delete')")
        public ResponseEntity<ApiResponse<PageResponse<DashboardFarmResponse>>> getAllDeleted(
                        @Valid
                        @ModelAttribute LookupFilterRequest filter) {
                return ResponseEntity.ok(
                                ApiResponse.success(
                                                farmService.getAllDeleted(filter),
                                                "Deleted farms retrieved"));
        }

        @PatchMapping("/dashboard/farms/{id}/restore")
        @PreAuthorize("hasAuthority('farm:delete')")
        public ResponseEntity<ApiResponse<Void>> restore(
                        @PathVariable Long id) {
                farmService.restore(id);
                return ResponseEntity.ok(
                                ApiResponse.success(
                                                null,
                                                "Farm restored"));
        }

        /**
         * Permanently deletes a trashed Farm permanently deleted. The row must already be soft-deleted — restoring
         * is still possible until this is called, and after it nothing is recoverable. Cloudinary
         * assets are removed here and only here; the soft delete keeps them so a restore brings
         * the item back intact.
         */
        @DeleteMapping("/dashboard/farms/{id}/permanent")
        @PreAuthorize("hasAuthority('farm:delete')")
        public ResponseEntity<Void> permanentDelete(@PathVariable Long id) {
                farmService.permanentDelete(id);
                return ResponseEntity.noContent().build();
        }
}
