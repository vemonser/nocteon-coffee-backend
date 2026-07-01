package com.nocteon.nocteon_api.pairing.controller;


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
import com.nocteon.nocteon_api.common.dto.LookupFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.pairing.dto.request.PairingRequest;
import com.nocteon.nocteon_api.pairing.dto.response.PairingResponse;
import com.nocteon.nocteon_api.pairing.dto.response.PairingResponseDashboard;
import com.nocteon.nocteon_api.pairing.service.PairingService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PairingController {

    private final PairingService pairingService;

    @GetMapping("/pairings")
    public ResponseEntity<ApiResponse<PageResponse<PairingResponse>>> getAll(
            @ModelAttribute LookupFilterRequest filter

    ) {
        return ResponseEntity.ok(ApiResponse.success(pairingService.getAll(filter), "Pairings retrieved"));
    }

    @GetMapping("/dashboard/pairings")
    @PreAuthorize("hasAuthority('pairing:read')")
    public ResponseEntity<ApiResponse<PageResponse<PairingResponseDashboard>>> getAllDashboard(
            @ModelAttribute LookupFilterRequest filter) {
        return ResponseEntity.ok(
                ApiResponse.success(pairingService.getAllDashboard(filter), "Pairings retrieved"));
    }


    @GetMapping("/pairings/{slug}")
    public ResponseEntity<ApiResponse<PairingResponse>> getBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.success(pairingService.getBySlug(slug), "Pairing retrieved"));
    }

    @PostMapping(value = "/dashboard/pairings",consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('pairing:create')")
    public ResponseEntity<ApiResponse<PairingResponse>> create(
            @Valid @RequestPart("data") PairingRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(pairingService.create(request, image), "Pairing created"));
    }

    @PutMapping(value = "/dashboard/pairings/{slug}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('pairing:update')")
    public ResponseEntity<ApiResponse<PairingResponse>> update(
            @PathVariable String slug,
            @Valid @RequestPart("data") PairingRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        return ResponseEntity.ok(ApiResponse.success(pairingService.update(slug, request, image), "Pairing updated"));
    }

    @PostMapping("/dashboard/pairings/{slug}/image")
    @PreAuthorize("hasAuthority('pairing:update')")
    public ResponseEntity<ApiResponse<PairingResponse>> uploadImage(
            @PathVariable String slug,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(pairingService.uploadImage(slug, file), "Image uploaded"));
    }

    @DeleteMapping("/dashboard/pairings/{slug}")
    @PreAuthorize("hasAuthority('pairing:delete')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String slug) {
        pairingService.delete(slug);
        return ResponseEntity.ok(ApiResponse.success(null, "Pairing deleted"));
    }
}