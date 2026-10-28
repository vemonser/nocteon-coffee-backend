package com.nocteon.nocteon_api.product.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
import com.nocteon.nocteon_api.product.dto.request.ProductFilterRequest;
import com.nocteon.nocteon_api.product.dto.request.ProductMediaRequest;
import com.nocteon.nocteon_api.product.dto.request.ProductRequest;
import com.nocteon.nocteon_api.product.dto.response.DashboardProductResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductCardResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductOptionOptionResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductResponse;
import com.nocteon.nocteon_api.product.service.ProductService;
import com.nocteon.nocteon_api.common.service.RichTextImageService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@Validated
@RequiredArgsConstructor
public class ProductController {

        private final ProductService productService;
        private final RichTextImageService richTextImageService;

        // =========================
        // Public
        // =========================

        @GetMapping("/products")
        public ResponseEntity<ApiResponse<PageResponse<ProductCardResponse>>> getAll(
                        @Valid
                        @ModelAttribute ProductFilterRequest filter) {

                return ResponseEntity.ok(
                                ApiResponse.success(
                                                productService.getAll(filter),
                                                "Products retrieved"));
        }

        @GetMapping("/products/{slug}")
        public ResponseEntity<ApiResponse<ProductResponse>> getBySlug(
                        @PathVariable String slug) {

                return ResponseEntity.ok(
                                ApiResponse.success(
                                                productService.getBySlug(slug),
                                                "Product retrieved"));
        }

        @GetMapping("/products/available-options")
        public ResponseEntity<ApiResponse<List<ProductOptionOptionResponse>>> getAvailableOptions(
                        @Valid
                        @ModelAttribute ProductFilterRequest filter) {

                return ResponseEntity.ok(
                                ApiResponse.success(
                                                productService.getAvailableOptions(filter),
                                                "Available options retrieved"));
        }
        // =========================
        // Dashboard
        // =========================

        @GetMapping("/dashboard/products")
        @PreAuthorize("hasAuthority('product:read')")
        public ResponseEntity<ApiResponse<PageResponse<ProductCardResponse>>> getAllDashboard(
                        @Valid
                        @ModelAttribute ProductFilterRequest filter) {

                return ResponseEntity.ok(
                                ApiResponse.success(
                                                productService.getAllDashboard(filter),
                                                "Products retrieved"));
        }

        @GetMapping("/dashboard/products/active-count")
        @PreAuthorize("hasAuthority('product:read')")
        public ResponseEntity<ApiResponse<Long>> getActiveCount() {

                return ResponseEntity.ok(
                                ApiResponse.success(
                                                productService.getActiveCount(),
                                                "Active products count retrieved"));
        }

        @GetMapping("/dashboard/products/{slug}")
        @PreAuthorize("hasAuthority('product:read')")
        public ResponseEntity<ApiResponse<DashboardProductResponse>> getDashboardBySlug(
                        @PathVariable String slug) {

                return ResponseEntity.ok(
                                ApiResponse.success(
                                                productService.getDashboardBySlug(slug),
                                                "Product retrieved"));
        }

        @PostMapping(value = "/dashboard/products/story-images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @PreAuthorize("hasAnyAuthority('product:create', 'product:update')")
        public ResponseEntity<ApiResponse<java.util.Map<String, String>>> uploadStoryImage(
                        @RequestPart("file") MultipartFile file) {
                return ResponseEntity.ok(ApiResponse.success(
                                java.util.Map.of("url", richTextImageService.upload(file, "products/story")),
                                "Image uploaded"));
        }

        /**
         * One photo for one variant. Uploaded on its own, before the product is saved, so the
         * variants step can show the picture immediately — the same contract the story editor
         * uses. The URL is then carried in the variant request.
         */
        @PostMapping(value = "/dashboard/products/variant-images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @PreAuthorize("hasAnyAuthority('product:create', 'product:update')")
        public ResponseEntity<ApiResponse<java.util.Map<String, String>>> uploadVariantImage(
                        @RequestPart("file") MultipartFile file) {
                return ResponseEntity.ok(ApiResponse.success(
                                java.util.Map.of("url", richTextImageService.upload(file, "products/variants")),
                                "Image uploaded"));
        }

        @PostMapping(value = "/dashboard/products", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @PreAuthorize("hasAuthority('product:create')")
        public ResponseEntity<ApiResponse<DashboardProductResponse>> create(
                        @Valid @RequestPart("request") ProductRequest request,
                        @RequestPart(value = "files", required = false) List<MultipartFile> files) {

                return ResponseEntity.status(HttpStatus.CREATED)
                                .body(ApiResponse.success(
                                                productService.create(request, files),
                                                "Product created"));
        }

        @PutMapping(value = "/dashboard/products/{slug}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @PreAuthorize("hasAuthority('product:update')")
        public ResponseEntity<ApiResponse<DashboardProductResponse>> update(
                        @PathVariable String slug,
                        @Valid @RequestPart("request") ProductRequest request,
                        @RequestPart(value = "files", required = false) List<MultipartFile> files) {

                return ResponseEntity.ok(
                                ApiResponse.success(
                                                productService.update(slug, request, files),
                                                "Product updated"));
        }

        @PatchMapping("/dashboard/products/{slug}/active")
        @PreAuthorize("hasAuthority('product:update')")
        public ResponseEntity<ApiResponse<DashboardProductResponse>> toggleActive(
                        @PathVariable String slug,
                        @RequestParam boolean value) {

                return ResponseEntity.ok(
                                ApiResponse.success(
                                                productService.toggleActive(slug, value),
                                                "Product status updated"));
        }

        @PatchMapping("/dashboard/products/{slug}/featured")
        @PreAuthorize("hasAuthority('product:update')")
        public ResponseEntity<ApiResponse<DashboardProductResponse>> toggleFeatured(
                        @PathVariable String slug,
                        @RequestParam boolean value) {

                return ResponseEntity.ok(
                                ApiResponse.success(
                                                productService.toggleFeatured(slug, value),
                                                "Product featured updated"));
        }

        @PostMapping(value = "/dashboard/products/{slug}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @PreAuthorize("hasAuthority('product:update')")
        public ResponseEntity<ApiResponse<DashboardProductResponse>> uploadMedia(
                        @PathVariable String slug,
                        @RequestPart("media") List<ProductMediaRequest> media,
                        @RequestPart(value = "files", required = false) List<MultipartFile> files) {

                return ResponseEntity.ok(
                                ApiResponse.success(
                                                productService.uploadMedia(slug, media, files),
                                                "Media uploaded"));
        }

        @DeleteMapping("/dashboard/products/media/{mediaId}")
        @PreAuthorize("hasAuthority('product:update')")
        public ResponseEntity<ApiResponse<Void>> deleteMedia(
                        @PathVariable Long mediaId) {

                productService.deleteMedia(mediaId);

                return ResponseEntity.ok(
                                ApiResponse.success(
                                                null,
                                                "Media deleted"));
        }

        @DeleteMapping("/dashboard/products/{slug}")
        @PreAuthorize("hasAuthority('product:delete')")
        public ResponseEntity<ApiResponse<Void>> delete(
                        @PathVariable String slug) {

                productService.delete(slug);

                return ResponseEntity.ok(
                                ApiResponse.success(
                                                null,
                                                "Product deleted"));
        }

        @GetMapping("/dashboard/products/deleted")
        @PreAuthorize("hasAuthority('product:delete')")
        public ResponseEntity<ApiResponse<PageResponse<DashboardProductResponse>>> getAllDeleted(
                        @Valid
                        @ModelAttribute LookupFilterRequest filter) {
                return ResponseEntity.ok(
                                ApiResponse.success(
                                                productService.getAllDeleted(filter),
                                                "Deleted products retrieved"));
        }

        @PatchMapping("/dashboard/products/{id}/restore")
        @PreAuthorize("hasAuthority('product:delete')")
        public ResponseEntity<ApiResponse<Void>> restore(
                        @PathVariable Long id) {

                productService.restore(id);

                return ResponseEntity.ok(
                                ApiResponse.success(
                                                null,
                                                "Product restored"));
        }

        /**
         * Permanently deletes a trashed Product permanently deleted. The row must already be soft-deleted — restoring
         * is still possible until this is called, and after it nothing is recoverable. Cloudinary
         * assets are removed here and only here; the soft delete keeps them so a restore brings
         * the item back intact.
         */
        @DeleteMapping("/dashboard/products/{id}/permanent")
        @PreAuthorize("hasAuthority('product:delete')")
        public ResponseEntity<Void> permanentDelete(@PathVariable Long id) {
                productService.permanentDelete(id);
                return ResponseEntity.noContent().build();
        }
}
