package com.nocteon.nocteon_api.processingMethod.controller;

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

import com.nocteon.nocteon_api.common.dto.ApiResponse;
import com.nocteon.nocteon_api.processingMethod.dto.request.ProcessingMethodRequest;
import com.nocteon.nocteon_api.processingMethod.dto.response.ProcessingMethodResponse;
import com.nocteon.nocteon_api.processingMethod.service.ProcessingMethodService;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/processing-methods")
@RequiredArgsConstructor
public class ProcessingMethodController {

    private final ProcessingMethodService processingMethodService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProcessingMethodResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(processingMethodService.getAll(), "Processing Method retrieved"));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ApiResponse<ProcessingMethodResponse>> getBySlug(@PathVariable String slug) {
        return ResponseEntity
                .ok(ApiResponse.success(processingMethodService.getBySlug(slug), "Processing Method retrieved"));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('processing_method:create')")
    public ResponseEntity<ApiResponse<ProcessingMethodResponse>> create(
            @Valid @RequestBody ProcessingMethodRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(processingMethodService.create(request), "Processing Method created"));
    }

    @PutMapping("/{slug}")
    @PreAuthorize("hasAuthority('processing_method:update')")
    public ResponseEntity<ApiResponse<ProcessingMethodResponse>> update(
            @PathVariable String slug,
            @Valid @RequestBody ProcessingMethodRequest request) {
        return ResponseEntity
                .ok(ApiResponse.success(processingMethodService.update(slug, request), "Processing Method updated"));
    }

    @DeleteMapping("/{slug}")
    @PreAuthorize("hasAuthority('processing_method:delete')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String slug) {
        processingMethodService.delete(slug);
        return ResponseEntity.ok(ApiResponse.success(null, "Processing Method deleted"));
    }
}