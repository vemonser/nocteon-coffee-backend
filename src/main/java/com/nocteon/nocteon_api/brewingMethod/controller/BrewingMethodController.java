package com.nocteon.nocteon_api.brewingMethod.controller;

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

import com.nocteon.nocteon_api.brewingMethod.dto.request.BrewingMethodRequest;
import com.nocteon.nocteon_api.brewingMethod.dto.response.BrewingMethodResponse;
import com.nocteon.nocteon_api.brewingMethod.service.BrewingMethodService;
import com.nocteon.nocteon_api.common.dto.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/brewing-methods")
@RequiredArgsConstructor
public class BrewingMethodController {

    private final BrewingMethodService brewingMethodService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<BrewingMethodResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(brewingMethodService.getAll(), "Brewing methods retrieved"));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ApiResponse<BrewingMethodResponse>> getBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.success(brewingMethodService.getBySlug(slug), "Brewing method retrieved"));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('brewing_method:create')")
    public ResponseEntity<ApiResponse<BrewingMethodResponse>> create(
            @Valid @RequestBody BrewingMethodRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(brewingMethodService.create(request), "Brewing method created"));
    }

    @PutMapping("/{slug}")
    @PreAuthorize("hasAuthority('brewing_method:update')")
    public ResponseEntity<ApiResponse<BrewingMethodResponse>> update(
            @PathVariable String slug,
            @Valid @RequestBody BrewingMethodRequest request) {
        return ResponseEntity.ok(ApiResponse.success(brewingMethodService.update(slug, request), "Brewing method updated"));
    }

    @DeleteMapping("/{slug}")
    @PreAuthorize("hasAuthority('brewing_method:delete')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String slug) {
        brewingMethodService.delete(slug);
        return ResponseEntity.ok(ApiResponse.success(null, "Brewing method deleted"));
    }
}