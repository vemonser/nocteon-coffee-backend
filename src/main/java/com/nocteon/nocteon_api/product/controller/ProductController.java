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
import org.springframework.web.multipart.MultipartFile;

import com.nocteon.nocteon_api.common.dto.ApiResponse;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.product.dto.request.ProductFilterRequest;
import com.nocteon.nocteon_api.product.dto.request.ProductMediaRequest;
import com.nocteon.nocteon_api.product.dto.request.ProductRequest;
import com.nocteon.nocteon_api.product.dto.response.DashboardProductResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductResponse;
import com.nocteon.nocteon_api.product.service.ProductService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;

    // ===== Public =====

    @GetMapping("/products")
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> getAll(
            @ModelAttribute ProductFilterRequest filter) {
        return ResponseEntity.ok(
                ApiResponse.success(productService.getAll(filter), "Products retrieved"));
    }

    @GetMapping("/products/{slug}")
    public ResponseEntity<ApiResponse<ProductResponse>> getBySlug(
            @PathVariable String slug) {
        return ResponseEntity.ok(
                ApiResponse.success(productService.getBySlug(slug), "Product retrieved"));
    }
    // ===== Dashboard =====

    @GetMapping("/dashboard/products")
    @PreAuthorize("hasAuthority('product:read')")
    public ResponseEntity<ApiResponse<PageResponse<DashboardProductResponse>>> getAllDashboard(
            @ModelAttribute ProductFilterRequest filter) {
        return ResponseEntity.ok(
                ApiResponse.success(productService.getAllDashboardFull(filter), "Products retrieved"));
    }

    @GetMapping("/dashboard/products/{slug}")
    @PreAuthorize("hasAuthority('product:read')")
    public ResponseEntity<ApiResponse<DashboardProductResponse>> getDashboardBySlug(
            @PathVariable String slug) {
        return ResponseEntity.ok(
                ApiResponse.success(productService.getDashboardBySlug(slug), "Product retrieved"));
    }

    @PostMapping(value = "/dashboard/products", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('product:create')")
    public ResponseEntity<ApiResponse<ProductResponse>> create(
            @Valid @RequestPart("request") ProductRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(productService.create(request,files), "Product created"));
    }

    @PutMapping(value = "/dashboard/products/{slug}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('product:update')")
    public ResponseEntity<ApiResponse<DashboardProductResponse>> update(
            @PathVariable String slug,
            @Valid @RequestPart("request") ProductRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {

        return ResponseEntity.ok(
                ApiResponse.success(productService.update(slug, request, files), "Product updated"));
    }

    @PatchMapping("/dashboard/products/{slug}/active")
    @PreAuthorize("hasAuthority('product:update')")
    public ResponseEntity<ApiResponse<DashboardProductResponse>> toggleActive(
            @PathVariable String slug,
            @RequestParam boolean value) {

        return ResponseEntity.ok(
                ApiResponse.success(productService.toggleActive(slug, value), "Product status updated"));
    }

    @PatchMapping("/dashboard/products/{slug}/featured")
    @PreAuthorize("hasAuthority('product:update')")
    public ResponseEntity<ApiResponse<DashboardProductResponse>> toggleFeatured(
            @PathVariable String slug,
            @RequestParam boolean value) {

        return ResponseEntity.ok(
                ApiResponse.success(productService.toggleFeatured(slug, value), "Product featured updated"));
    }

    @PostMapping(value = "/dashboard/products/{slug}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('product:update')")
    public ResponseEntity<ApiResponse<ProductResponse>> uploadMedia(
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
    public ResponseEntity<ApiResponse<Void>> deleteMedia(@PathVariable Long mediaId) {
        productService.deleteMedia(mediaId);
        return ResponseEntity.ok(ApiResponse.success(null, "Media deleted"));
    }

    @DeleteMapping("/dashboard/products/{slug}")
    @PreAuthorize("hasAuthority('product:delete')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String slug) {
        productService.delete(slug);
        return ResponseEntity.ok(ApiResponse.success(null, "Product deleted"));
    }

}
