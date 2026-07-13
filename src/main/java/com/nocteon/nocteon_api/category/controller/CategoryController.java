package com.nocteon.nocteon_api.category.controller;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.nocteon.nocteon_api.category.dto.request.CategoryRequest;
import com.nocteon.nocteon_api.category.dto.response.CategoryResponse;
import com.nocteon.nocteon_api.category.dto.response.DashboardCategoryResponse;
import com.nocteon.nocteon_api.category.service.CategoryService;
import com.nocteon.nocteon_api.common.dto.ApiResponse;
import com.nocteon.nocteon_api.common.dto.LookupFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.product.dto.response.ProductCardResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class CategoryController {

        private final CategoryService categoryService;

        // ===== Public Endpoints =====

        @GetMapping("/categories")
        public ResponseEntity<ApiResponse<PageResponse<CategoryResponse>>> getAll(
                        @ModelAttribute LookupFilterRequest filter

        ) {
                return ResponseEntity.ok(ApiResponse.success(categoryService.getAll(filter), "Categories retrieved"));
        }

        @GetMapping("/categories/{slug}")
        public ResponseEntity<ApiResponse<CategoryResponse>> getBySlug(
                        @PathVariable String slug) {
                return ResponseEntity.ok(
                                ApiResponse.success(categoryService.getBySlug(slug), "Category retrieved"));
        }

        @GetMapping("/categories/{slug}/products")
        public ResponseEntity<ApiResponse<PageResponse<ProductCardResponse>>> getCategoryProducts(
                        @PathVariable String slug,
                        @ModelAttribute LookupFilterRequest filter) {
                Page<ProductCardResponse> page = categoryService.getProductsByCategorySlug(slug, filter.toPageable());
                return ResponseEntity.ok(ApiResponse.success(PageResponse.of(page), "Products retrieved"));
        }

        // ===== Dashboard Endpoints =====

        @GetMapping("/dashboard/categories")
        @PreAuthorize("hasAuthority('category:read')")
        public ResponseEntity<ApiResponse<PageResponse<DashboardCategoryResponse>>> getAllDashboard(
                        @ModelAttribute LookupFilterRequest filter) {
                return ResponseEntity.ok(
                                ApiResponse.success(categoryService.getAllDashboard(filter), "Categories retrieved"));
        }

        @GetMapping("/dashboard/categories/{slug}")
        @PreAuthorize("hasAuthority('category:read')")
        public ResponseEntity<ApiResponse<DashboardCategoryResponse>> getDashboardBySlug(@PathVariable String slug) {
                return ResponseEntity.ok(
                                ApiResponse.success(categoryService.getDashboardBySlug(slug), "Category retrieved"));
        }

        @PostMapping(value = "/dashboard/categories", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @PreAuthorize("hasAuthority('category:create')")
        public ResponseEntity<ApiResponse<CategoryResponse>> create(
                        @Valid @RequestPart("data") CategoryRequest request,
                        @RequestPart(value = "image", required = false) MultipartFile image) {
                return ResponseEntity.status(HttpStatus.CREATED)
                                .body(ApiResponse.success(categoryService.create(request, image), "Category created"));
        }

        @PutMapping(value = "/dashboard/categories/{slug}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @PreAuthorize("hasAuthority('category:update')")
        public ResponseEntity<ApiResponse<CategoryResponse>> update(
                        @PathVariable String slug,
                        @Valid @RequestPart("data") CategoryRequest request,
                        @RequestPart(value = "image", required = false) MultipartFile image) {
                return ResponseEntity.ok(
                                ApiResponse.success(categoryService.update(slug, request, image), "Category updated"));
        }

        @PostMapping("/dashboard/categories/{slug}/image")
        @PreAuthorize("hasAuthority('category:update')")
        public ResponseEntity<ApiResponse<CategoryResponse>> uploadImage(
                        @PathVariable String slug,
                        @RequestParam("file") MultipartFile file) {
                return ResponseEntity.ok(
                                ApiResponse.success(categoryService.uploadImage(slug, file), "Image uploaded"));
        }

        @DeleteMapping("/dashboard/categories/{slug}")
        @PreAuthorize("hasAuthority('category:delete')")
        public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String slug) {
                categoryService.delete(slug);
                return ResponseEntity.ok(ApiResponse.success(null, "Category deleted"));
        }

}
