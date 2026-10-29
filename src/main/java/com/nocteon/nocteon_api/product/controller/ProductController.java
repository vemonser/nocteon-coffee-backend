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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;

import com.nocteon.nocteon_api.common.dto.ApiResponse;
import com.nocteon.nocteon_api.common.dto.LookupFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.product.dto.request.ProductFilterRequest;
import com.nocteon.nocteon_api.product.dto.request.ProductMediaRequest;
import com.nocteon.nocteon_api.product.dto.request.ProductRequest;
import com.nocteon.nocteon_api.product.dto.request.ProductTagRequest;
import com.nocteon.nocteon_api.product.dto.request.StockAdjustmentRequest;
import com.nocteon.nocteon_api.product.dto.response.DashboardProductResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductCardResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductOptionOptionResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductTagResponseDashboard;
import com.nocteon.nocteon_api.product.dto.response.ProductResponse;
import com.nocteon.nocteon_api.product.dto.response.StockMovementResponse;
import com.nocteon.nocteon_api.product.dto.response.summary.ProductTagSummary;
import com.nocteon.nocteon_api.product.entity.InventoryMovement;
import com.nocteon.nocteon_api.product.enums.InventoryMovementReason;
import com.nocteon.nocteon_api.product.enums.InventoryMovementSource;
import com.nocteon.nocteon_api.common.security.ActorResolver;
import com.nocteon.nocteon_api.product.service.InventoryMovementService;
import com.nocteon.nocteon_api.product.service.ProductService;
import com.nocteon.nocteon_api.product.service.ProductTagService;
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
        private final ProductTagService productTagService;
        private final InventoryMovementService inventoryMovementService;

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

        /**
         * FE-STOREFRONT: the public tag vocabulary, localized, so the storefront can render a
         * filter without a dashboard session. Gated on nothing — tags are public metadata.
         */
        @GetMapping("/product-tags")
        public ResponseEntity<ApiResponse<List<ProductTagSummary>>> getPublicTags() {
                return ResponseEntity.ok(
                                ApiResponse.success(
                                                productTagService.getTagSummaries(
                                                        LocaleContextHolder.getLocale().getLanguage()),
                                                "Tags retrieved"));
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

        /**
         * FE-15: the barcodes of the variants of these products, for the products CSV export.
         *
         * <p>Read-only and gated exactly like the list it serves ({@code product:read}): it exposes
         * nothing the dashboard detail does not already return, one column of one relation. The
         * export walks the filtered list pages and asks for this map once, instead of reading every
         * product detail for one column.
         */
        @GetMapping("/dashboard/products/variant-barcodes")
        @PreAuthorize("hasAuthority('product:read')")
        public ResponseEntity<ApiResponse<java.util.Map<Long, List<String>>>> getVariantBarcodes(
                        @RequestParam(name = "productIds", required = false) List<Long> productIds) {

                return ResponseEntity.ok(
                                ApiResponse.success(
                                                productService.getVariantBarcodes(productIds),
                                                "Variant barcodes retrieved"));
        }

        /**
         * FE-13: the last movements of a variant, newest first.
         *
         * <p>Gated like the product detail it hangs off ({@code product:read}): the history of a
         * stock number is part of reading the product, not a separate privilege.
         */
        @GetMapping("/dashboard/products/variants/{variantId}/movements")
        @PreAuthorize("hasAuthority('product:read')")
        public ResponseEntity<ApiResponse<List<StockMovementResponse>>> getVariantMovements(
                        @PathVariable Long variantId,
                        @RequestParam(name = "limit", required = false, defaultValue = "10") int limit) {

                return ResponseEntity.ok(
                                ApiResponse.success(
                                                inventoryMovementService.recent(variantId, limit).stream()
                                                                .map(ProductController::toMovementResponse)
                                                                .toList(),
                                                "Stock movements retrieved"));
        }

        /**
         * FE-13: change a variant's stock by hand.
         *
         * <p>The reason is REQUIRED — a blank one is refused with 400 before anything is written —
         * and the row records who did it, from what number to what number, in the same transaction
         * as the change. {@code product:update}, the authority the product form already needs.
         */
        @PostMapping("/dashboard/products/variants/{variantId}/stock-adjustment")
        @PreAuthorize("hasAuthority('product:update')")
        public ResponseEntity<ApiResponse<StockMovementResponse>> adjustStock(
                        @PathVariable Long variantId,
                        @Valid @RequestBody StockAdjustmentRequest request) {

                InventoryMovementService.StockChange change = inventoryMovementService.adjustManually(
                                variantId, request.getDelta(), request.getReason(), ActorResolver.currentActor());

                return ResponseEntity.ok(
                                ApiResponse.success(
                                                StockMovementResponse.builder()
                                                                .variantId(change.variantId())
                                                                .delta(change.delta())
                                                                .stockBefore(change.before())
                                                                .stockAfter(change.after())
                                                                .reason(InventoryMovementReason.MANUAL_ADJUSTMENT)
                                                                .reasonNote(request.getReason().trim())
                                                                .actor(ActorResolver.currentActor())
                                                                .sourceType(InventoryMovementSource.MANUAL)
                                                                .build(),
                                                "Stock adjusted"));
        }

        private static StockMovementResponse toMovementResponse(InventoryMovement movement) {
                return StockMovementResponse.builder()
                                .id(movement.getId())
                                .variantId(movement.getVariantId())
                                .delta(movement.getDelta())
                                .stockBefore(movement.getStockBefore())
                                .stockAfter(movement.getStockAfter())
                                .reason(movement.getReason())
                                .reasonNote(movement.getReasonNote())
                                .actor(movement.getActor())
                                .sourceType(movement.getSourceType())
                                .sourceId(movement.getSourceId())
                                .createdAt(movement.getCreatedAt())
                                .build();
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

        /**
         * FE-12: copy a product into an inactive draft. `product:create` is the grant — the action
         * creates a product, even though it starts from an existing one.
         */
        @PostMapping("/dashboard/products/{slug}/duplicate")
        @PreAuthorize("hasAuthority('product:create')")
        public ResponseEntity<ApiResponse<DashboardProductResponse>> duplicate(
                        @PathVariable String slug) {

                return ResponseEntity.status(HttpStatus.CREATED).body(
                                ApiResponse.success(
                                                productService.duplicate(slug),
                                                "Product duplicated"));
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

        // =========================
        // Tags (FE-10)
        // =========================

        @GetMapping("/dashboard/product-tags")
        @PreAuthorize("hasAuthority('product:read')")
        public ResponseEntity<ApiResponse<List<ProductTagResponseDashboard>>> getTags() {
                return ResponseEntity.ok(
                                ApiResponse.success(productTagService.getTags(), "Tags retrieved"));
        }

        @PostMapping("/dashboard/product-tags")
        @PreAuthorize("hasAuthority('product:create')")
        public ResponseEntity<ApiResponse<ProductTagResponseDashboard>> createTag(
                        @Valid @RequestBody ProductTagRequest request) {
                return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                                productTagService.createTag(request), "Tag created"));
        }

        @PutMapping("/dashboard/product-tags/{id}")
        @PreAuthorize("hasAuthority('product:update')")
        public ResponseEntity<ApiResponse<ProductTagResponseDashboard>> renameTag(
                        @PathVariable Long id,
                        @Valid @RequestBody ProductTagRequest request) {
                return ResponseEntity.ok(ApiResponse.success(
                                productTagService.renameTag(id, request), "Tag updated"));
        }

        @DeleteMapping("/dashboard/product-tags/{id}")
        @PreAuthorize("hasAuthority('product:delete')")
        public ResponseEntity<ApiResponse<Void>> deleteTag(@PathVariable Long id) {
                productTagService.deleteTag(id);
                return ResponseEntity.ok(ApiResponse.success(null, "Tag deleted"));
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
