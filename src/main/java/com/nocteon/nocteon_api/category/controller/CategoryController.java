package com.nocteon.nocteon_api.category.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
import com.nocteon.nocteon_api.category.service.CategoryService;
import com.nocteon.nocteon_api.common.dto.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getAllCategories() {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getAllCategories(), "Categories retrieved"));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ApiResponse<CategoryResponse>> getCategory(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getCategory(slug), "Category retrieved"));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('category:create')")
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            @Valid @RequestPart("data") CategoryRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        categoryService.createCategory(request, image),
                        "Category created"));
    }

    @PutMapping(value = "/{slug}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('category:update')")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
            @PathVariable String slug,
            @Valid @RequestPart("data") CategoryRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        categoryService.updateCategory(slug, request, image),
                        "Category updated"));
    }

    @PostMapping("/{slug}/image")
    @PreAuthorize("hasAuthority('category:update')")
    public ResponseEntity<ApiResponse<CategoryResponse>> uploadCategoryImage(
            @PathVariable String slug,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        categoryService.uploadImage(slug, file),
                        "Image uploaded successfully"));
    }

    @DeleteMapping("/{slug}")
    @PreAuthorize("hasAuthority('category:delete')")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(
            @PathVariable String slug) {
        categoryService.deleteCategory(slug);
        return ResponseEntity.ok(ApiResponse.success(null, "Category deleted"));
    }
}
