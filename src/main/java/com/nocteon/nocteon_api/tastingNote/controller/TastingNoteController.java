package com.nocteon.nocteon_api.tastingNote.controller;


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
import com.nocteon.nocteon_api.tastingNote.dto.request.TastingNoteRequest;
import com.nocteon.nocteon_api.tastingNote.dto.response.TastingNoteResponse;
import com.nocteon.nocteon_api.tastingNote.dto.response.TastingNoteResponseDashboard;
import com.nocteon.nocteon_api.tastingNote.service.TastingNoteService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@Validated
@RequiredArgsConstructor
public class TastingNoteController {

    private final TastingNoteService tastingNoteService;

    @GetMapping("/tasting-notes")
    public ResponseEntity<ApiResponse<PageResponse<TastingNoteResponse>>> getAll(
            @Valid
            @ModelAttribute LookupFilterRequest filter) {
        return ResponseEntity.ok(
                ApiResponse.success(tastingNoteService.getAll(filter), "Tasting notes retrieved"));
    }

    @GetMapping("/tasting-notes/{slug}")
    public ResponseEntity<ApiResponse<TastingNoteResponse>> getBySlug(
            @PathVariable String slug) {
        return ResponseEntity.ok(
                ApiResponse.success(tastingNoteService.getBySlug(slug), "Tasting note retrieved"));
    }

    @GetMapping("/dashboard/tasting-notes")
    @PreAuthorize("hasAuthority('tasting_note:read')")
    public ResponseEntity<ApiResponse<PageResponse<TastingNoteResponseDashboard>>> getAllDashboard(
            @Valid
            @ModelAttribute LookupFilterRequest filter) {
        return ResponseEntity.ok(
                ApiResponse.success(tastingNoteService.getAllDashboard(filter), "Tasting notes retrieved"));
    }

    @GetMapping("/dashboard/tasting-notes/{slug}")
    @PreAuthorize("hasAuthority('tasting_note:read')")
    public ResponseEntity<ApiResponse<TastingNoteResponseDashboard>> getDashboardBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(
                ApiResponse.success(tastingNoteService.getDashboardBySlug(slug), "Tasting note retrieved"));
    }

    @PostMapping("/dashboard/tasting-notes")
    @PreAuthorize("hasAuthority('tasting_note:create')")
    public ResponseEntity<ApiResponse<TastingNoteResponseDashboard>> create(
            @Valid @RequestBody TastingNoteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(tastingNoteService.create(request), "Tasting note created"));
    }

    @PutMapping("/dashboard/tasting-notes/{slug}")
    @PreAuthorize("hasAuthority('tasting_note:update')")
    public ResponseEntity<ApiResponse<TastingNoteResponseDashboard>> update(
            @PathVariable String slug,
            @Valid @RequestBody TastingNoteRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(tastingNoteService.update(slug, request), "Tasting note updated"));
    }

    @DeleteMapping("/dashboard/tasting-notes/{slug}")
    @PreAuthorize("hasAuthority('tasting_note:delete')")
    public ResponseEntity<Void> delete(@PathVariable String slug) {
        tastingNoteService.delete(slug);
        return ResponseEntity.noContent().build();
    }

        @GetMapping("/dashboard/tasting-notes/deleted")
        @PreAuthorize("hasAuthority('tasting_note:delete')")
        public ResponseEntity<ApiResponse<PageResponse<TastingNoteResponseDashboard>>> getAllDeleted(
                        @Valid
                        @ModelAttribute LookupFilterRequest filter) {
                return ResponseEntity.ok(
                                ApiResponse.success(
                                                tastingNoteService.getAllDeleted(filter),
                                                "Deleted tasting notes retrieved"));
        }

        @PatchMapping("/dashboard/tasting-notes/{id}/restore")
        @PreAuthorize("hasAuthority('tasting_note:delete')")
        public ResponseEntity<ApiResponse<Void>> restore(
                        @PathVariable Long id) {
                tastingNoteService.restore(id);
                return ResponseEntity.ok(
                                ApiResponse.success(
                                                null,
                                                "Tasting note restored"));
        }

        /**
         * Permanently deletes a trashed Tasting note permanently deleted. The row must already be soft-deleted — restoring
         * is still possible until this is called, and after it nothing is recoverable. Cloudinary
         * assets are removed here and only here; the soft delete keeps them so a restore brings
         * the item back intact.
         */
        @DeleteMapping("/dashboard/tasting-notes/{id}/permanent")
        @PreAuthorize("hasAuthority('tasting_note:delete')")
        public ResponseEntity<Void> permanentDelete(@PathVariable Long id) {
                tastingNoteService.permanentDelete(id);
                return ResponseEntity.noContent().build();
        }
}
