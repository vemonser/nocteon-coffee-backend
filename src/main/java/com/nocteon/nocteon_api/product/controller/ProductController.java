package com.nocteon.nocteon_api.product.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.nocteon.nocteon_api.common.dto.ApiResponse;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.product.dto.request.ProductFilterRequest;
import com.nocteon.nocteon_api.product.dto.request.ProductRequest;
import com.nocteon.nocteon_api.product.dto.response.ProductResponse;
import com.nocteon.nocteon_api.product.enums.MediaType;
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
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> getAllDashboard(
            @ModelAttribute ProductFilterRequest filter) {
        return ResponseEntity.ok(
                ApiResponse.success(productService.getAllDashboard(filter), "Products retrieved"));
    }

    @PostMapping("/dashboard/products")
    @PreAuthorize("hasAuthority('product:create')")
    public ResponseEntity<ApiResponse<ProductResponse>> create(
            @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(productService.create(request), "Product created"));
    }

    @PostMapping("/dashboard/products/{slug}/media")
    @PreAuthorize("hasAuthority('product:update')")
    public ResponseEntity<ApiResponse<ProductResponse>> uploadMedia(
            @PathVariable String slug,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "type", defaultValue = "IMAGE") MediaType type,
            @RequestParam(value = "isPrimary", defaultValue = "false") boolean isPrimary) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        productService.uploadMedia(slug, file, type, isPrimary),
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
