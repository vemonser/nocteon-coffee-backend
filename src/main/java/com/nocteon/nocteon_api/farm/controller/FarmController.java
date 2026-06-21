package com.nocteon.nocteon_api.farm.controller;

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
import com.nocteon.nocteon_api.farm.dto.request.FarmRequest;
import com.nocteon.nocteon_api.farm.dto.response.FarmResponse;
import com.nocteon.nocteon_api.farm.service.FarmService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/farms")
@RequiredArgsConstructor
public class FarmController {

    private final FarmService farmService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<FarmResponse>>> getAll() {
        return ResponseEntity.ok(
                ApiResponse.success(farmService.getAll(), "Farms retrieved"));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ApiResponse<FarmResponse>> getBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(
                ApiResponse.success(farmService.getBySlug(slug), "Farm retrieved"));
    }

    @GetMapping("/origin/{originSlug}")
    public ResponseEntity<ApiResponse<List<FarmResponse>>> getByOrigin(
            @PathVariable String originSlug) {
        return ResponseEntity.ok(
                ApiResponse.success(farmService.getByOrigin(originSlug), "Farms retrieved"));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('farm:create')")
    public ResponseEntity<ApiResponse<FarmResponse>> create(
            @Valid @RequestPart("data") FarmRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(farmService.create(request, image), "Farm created"));
    }

    @PutMapping(value = "/{slug}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('farm:update')")
    public ResponseEntity<ApiResponse<FarmResponse>> update(
            @PathVariable String slug,
            @Valid @RequestPart("data") FarmRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        return ResponseEntity.ok(
                ApiResponse.success(farmService.update(slug, request, image), "Farm updated"));
    }

    @PostMapping("/{slug}/image")
    @PreAuthorize("hasAuthority('farm:update')")
    public ResponseEntity<ApiResponse<FarmResponse>> uploadFarmImage(
            @PathVariable String slug,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        farmService.uploadImage(slug, file),
                        "Image uploaded successfully"));
    }

    @DeleteMapping("/{slug}")
    @PreAuthorize("hasAuthority('farm:delete')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String slug) {
        farmService.delete(slug);
        return ResponseEntity.ok(ApiResponse.success(null, "Farm deleted"));
    }

}
