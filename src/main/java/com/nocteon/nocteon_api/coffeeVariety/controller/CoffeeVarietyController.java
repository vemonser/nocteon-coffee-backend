package com.nocteon.nocteon_api.coffeeVariety.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nocteon.nocteon_api.coffeeVariety.dto.request.CoffeeVarietyRequest;
import com.nocteon.nocteon_api.coffeeVariety.dto.response.CoffeeVarietyResponse;
import com.nocteon.nocteon_api.coffeeVariety.service.CoffeeVarietyService;
import com.nocteon.nocteon_api.common.dto.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/coffee-varieties")
@RequiredArgsConstructor
public class CoffeeVarietyController {

    private final CoffeeVarietyService coffeeVarietyService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CoffeeVarietyResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(coffeeVarietyService.getAll(), "Coffee varieties retrieved"));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ApiResponse<CoffeeVarietyResponse>> getBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.success(coffeeVarietyService.getBySlug(slug), "Coffee variety retrieved"));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('coffee_variety:create')")
    public ResponseEntity<ApiResponse<CoffeeVarietyResponse>> create(
            @Valid @RequestBody CoffeeVarietyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(coffeeVarietyService.create(request), "Coffee variety created"));
    }

    @PutMapping("/{slug}")
    @PreAuthorize("hasAuthority('coffee_variety:update')")
    public ResponseEntity<ApiResponse<CoffeeVarietyResponse>> update(
            @PathVariable String slug,
            @Valid @RequestBody CoffeeVarietyRequest request) {
        return ResponseEntity.ok(ApiResponse.success(coffeeVarietyService.update(slug, request), "Coffee variety updated"));
    }

    @DeleteMapping("/{slug}")
    @PreAuthorize("hasAuthority('coffee_variety:delete')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String slug) {
        coffeeVarietyService.delete(slug);
        return ResponseEntity.ok(ApiResponse.success(null, "Coffee variety deleted"));
    }
}