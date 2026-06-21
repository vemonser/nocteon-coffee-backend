package com.nocteon.nocteon_api.origin.controller;

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

import com.nocteon.nocteon_api.common.dto.ApiResponse;
import com.nocteon.nocteon_api.origin.dto.request.OriginRequest;
import com.nocteon.nocteon_api.origin.dto.response.OriginResponse;
import com.nocteon.nocteon_api.origin.service.OriginService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/origins")
@RequiredArgsConstructor
public class OriginController {

    private final OriginService originService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<OriginResponse>>> getAllOrigins() {
        return ResponseEntity.ok(ApiResponse.success(originService.getAll(), "Origin retrieved"));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ApiResponse<OriginResponse>> getOrigin(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.success(originService.getOrigin(slug), "Origin retrieved"));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('origin:create')")
    public ResponseEntity<ApiResponse<OriginResponse>> createOrigin(
            @Valid @RequestPart("data") OriginRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        originService.create(request, image),
                        "Origin created"));
    }

    @PutMapping(value = "/{slug}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('origin:update')")
    public ResponseEntity<ApiResponse<OriginResponse>> updateOrigin(
            @PathVariable String slug,
            @Valid @RequestPart("data") OriginRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        originService.update(slug, request, image),
                        "Origin updated"));
    }

    @PostMapping("/{slug}/image")
    @PreAuthorize("hasAuthority('origin:update')")
    public ResponseEntity<ApiResponse<OriginResponse>> uploadOriginImage(
            @PathVariable String slug,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        originService.uploadImage(slug, file),
                        "Image uploaded successfully"));
    }

    @DeleteMapping("/{slug}")
    @PreAuthorize("hasAuthority('origin:delete')")
    public ResponseEntity<ApiResponse<Void>> deleteOrigin(
            @PathVariable String slug) {
        originService.delete(slug);
        return ResponseEntity.ok(ApiResponse.success(null, "Origin deleted"));
    }
}
