package com.nocteon.nocteon_api.farm.controller;


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

import com.nocteon.nocteon_api.common.dto.ApiResponse;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.farm.dto.request.FarmFilterRequest;
import com.nocteon.nocteon_api.farm.dto.request.FarmRequest;
import com.nocteon.nocteon_api.farm.dto.response.DashboardFarmResponse;
import com.nocteon.nocteon_api.farm.dto.response.FarmResponse;
import com.nocteon.nocteon_api.farm.service.FarmService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FarmController {

        private final FarmService farmService;

        @GetMapping("/farms")
        public ResponseEntity<ApiResponse<PageResponse<FarmResponse>>> getAll(
                        @ModelAttribute FarmFilterRequest filter) {
                return ResponseEntity.ok(
                                ApiResponse.success(farmService.getAll(filter), "Farms retrieved"));
        }

        @GetMapping("/farms/{slug}")
        public ResponseEntity<ApiResponse<FarmResponse>> getBySlug(
                        @PathVariable String slug) {
                return ResponseEntity.ok(
                                ApiResponse.success(farmService.getBySlug(slug), "Farm retrieved"));
        }

        @GetMapping("/dashboard/farms")
        @PreAuthorize("hasAuthority('farm:read')")
        public ResponseEntity<ApiResponse<PageResponse<DashboardFarmResponse>>> getAllDashboard(
                        @ModelAttribute FarmFilterRequest filter) {
                return ResponseEntity.ok(
                                ApiResponse.success(farmService.getAllDashboard(filter), "Farms retrieved"));
        }

        @PostMapping(value = "/dashboard/farms", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @PreAuthorize("hasAuthority('farm:create')")
        public ResponseEntity<ApiResponse<FarmResponse>> create(
                        @Valid @RequestPart("data") FarmRequest request,
                        @RequestPart(value = "image", required = false) MultipartFile image) {
                return ResponseEntity.status(HttpStatus.CREATED)
                                .body(ApiResponse.success(farmService.create(request, image), "Farm created"));
        }

        @PutMapping(value = "/dashboard/farms/{slug}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @PreAuthorize("hasAuthority('farm:update')")
        public ResponseEntity<ApiResponse<FarmResponse>> update(
                        @PathVariable String slug,
                        @Valid @RequestPart("data") FarmRequest request,
                        @RequestPart(value = "image", required = false) MultipartFile image) {
                return ResponseEntity.ok(
                                ApiResponse.success(farmService.update(slug, request, image), "Farm updated"));
        }

        @PostMapping("/dashboard/farms/{slug}/image")
        @PreAuthorize("hasAuthority('farm:update')")
        public ResponseEntity<ApiResponse<FarmResponse>> uploadImage(
                        @PathVariable String slug,
                        @RequestParam("file") MultipartFile file) {
                return ResponseEntity.ok(
                                ApiResponse.success(farmService.uploadImage(slug, file), "Image uploaded"));
        }

        @DeleteMapping("/dashboard/farms/{slug}")
        @PreAuthorize("hasAuthority('farm:delete')")
        public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String slug) {
                farmService.delete(slug);
                return ResponseEntity.ok(ApiResponse.success(null, "Farm deleted"));
        }
}