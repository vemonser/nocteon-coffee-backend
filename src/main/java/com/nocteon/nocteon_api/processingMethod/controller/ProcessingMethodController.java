package com.nocteon.nocteon_api.processingMethod.controller;


import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import com.nocteon.nocteon_api.common.dto.ApiResponse;
import com.nocteon.nocteon_api.common.dto.LookupFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.processingMethod.dto.request.ProcessingMethodRequest;
import com.nocteon.nocteon_api.processingMethod.dto.response.ProcessingMethodResponse;
import com.nocteon.nocteon_api.processingMethod.dto.response.ProcessingMethodResponseDashboard;
import com.nocteon.nocteon_api.processingMethod.service.ProcessingMethodService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@Validated
@RequiredArgsConstructor
public class ProcessingMethodController {

    private final ProcessingMethodService processingMethodService;

    @GetMapping("/processing-methods")
    public ResponseEntity<ApiResponse<PageResponse<ProcessingMethodResponse>>> getAll(
            @Valid
            @ModelAttribute LookupFilterRequest filter) {
        return ResponseEntity.ok(
                ApiResponse.success(processingMethodService.getAll(filter), "Processing methods retrieved"));
    }

    @GetMapping("/processing-methods/{slug}")
    public ResponseEntity<ApiResponse<ProcessingMethodResponse>> getBySlug(
            @PathVariable String slug) {
        return ResponseEntity.ok(
                ApiResponse.success(processingMethodService.getBySlug(slug), "Processing method retrieved"));
    }

    @GetMapping("/dashboard/processing-methods")
    @PreAuthorize("hasAuthority('processing_method:read')")
    public ResponseEntity<ApiResponse<PageResponse<ProcessingMethodResponseDashboard>>> getAllDashboard(
            @Valid
            @ModelAttribute LookupFilterRequest filter) {
        return ResponseEntity.ok(
                ApiResponse.success(processingMethodService.getAllDashboard(filter), "Processing methods retrieved"));
    }

    @GetMapping("/dashboard/processing-methods/{slug}")
    @PreAuthorize("hasAuthority('processing_method:read')")
    public ResponseEntity<ApiResponse<ProcessingMethodResponseDashboard>> getDashboardBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(
                ApiResponse.success(processingMethodService.getDashboardBySlug(slug), "Processing method retrieved"));
    }

    @PostMapping("/dashboard/processing-methods")
    @PreAuthorize("hasAuthority('processing_method:create')")
    public ResponseEntity<ApiResponse<ProcessingMethodResponseDashboard>> create(
            @Valid @RequestBody ProcessingMethodRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(processingMethodService.create(request), "Processing method created"));
    }

    @PutMapping("/dashboard/processing-methods/{slug}")
    @PreAuthorize("hasAuthority('processing_method:update')")
    public ResponseEntity<ApiResponse<ProcessingMethodResponseDashboard>> update(
            @PathVariable String slug,
            @Valid @RequestBody ProcessingMethodRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(processingMethodService.update(slug, request), "Processing method updated"));
    }

    @DeleteMapping("/dashboard/processing-methods/{slug}")
    @PreAuthorize("hasAuthority('processing_method:delete')")
    public ResponseEntity<Void> delete(@PathVariable String slug) {
        processingMethodService.delete(slug);
        return ResponseEntity.noContent().build();
    }

        @GetMapping("/dashboard/processing-methods/deleted")
        @PreAuthorize("hasAuthority('processing_method:delete')")
        public ResponseEntity<ApiResponse<PageResponse<ProcessingMethodResponseDashboard>>> getAllDeleted(
                        @Valid
                        @ModelAttribute LookupFilterRequest filter) {
                return ResponseEntity.ok(
                                ApiResponse.success(
                                                processingMethodService.getAllDeleted(filter),
                                                "Deleted processing methods retrieved"));
        }

        @PatchMapping("/dashboard/processing-methods/{id}/restore")
        @PreAuthorize("hasAuthority('processing_method:delete')")
        public ResponseEntity<ApiResponse<Void>> restore(
                        @PathVariable Long id) {
                processingMethodService.restore(id);
                return ResponseEntity.ok(
                                ApiResponse.success(
                                                null,
                                                "Processing method restored"));
        }

        /**
         * Permanently deletes a trashed Processing method permanently deleted. The row must already be soft-deleted — restoring
         * is still possible until this is called, and after it nothing is recoverable. Cloudinary
         * assets are removed here and only here; the soft delete keeps them so a restore brings
         * the item back intact.
         */
        @DeleteMapping("/dashboard/processing-methods/{id}/permanent")
        @PreAuthorize("hasAuthority('processing_method:delete')")
        public ResponseEntity<Void> permanentDelete(@PathVariable Long id) {
                processingMethodService.permanentDelete(id);
                return ResponseEntity.noContent().build();
        }
}
