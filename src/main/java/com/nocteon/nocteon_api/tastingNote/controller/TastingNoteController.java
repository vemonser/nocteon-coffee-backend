package com.nocteon.nocteon_api.tastingNote.controller;

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
import com.nocteon.nocteon_api.tastingNote.dto.request.TastingNoteRequest;
import com.nocteon.nocteon_api.tastingNote.dto.response.TastingNoteResponse;
import com.nocteon.nocteon_api.tastingNote.service.TastingNoteService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/tasting-notes")
@RequiredArgsConstructor
public class TastingNoteController {

    private final TastingNoteService tastingNoteService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<TastingNoteResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(tastingNoteService.getAll(), "Tasting Note retrieved"));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ApiResponse<TastingNoteResponse>> getBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.success(tastingNoteService.getBySlug(slug), "Tasting Note retrieved"));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('tasting_note:create')")
    public ResponseEntity<ApiResponse<TastingNoteResponse>> create(
            @Valid @RequestBody TastingNoteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(tastingNoteService.create(request), "Tasting Note created"));
    }

    @PutMapping("/{slug}")
    @PreAuthorize("hasAuthority('tasting_note:update')")
    public ResponseEntity<ApiResponse<TastingNoteResponse>> update(
            @PathVariable String slug,
            @Valid @RequestBody TastingNoteRequest request) {
        return ResponseEntity.ok(ApiResponse.success(tastingNoteService.update(slug, request), "Tasting Note updated"));
    }

    @DeleteMapping("/{slug}")
    @PreAuthorize("hasAuthority('tasting_note:delete')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String slug) {
        tastingNoteService.delete(slug);
        return ResponseEntity.ok(ApiResponse.success(null, "Tasting Note deleted"));
    }
}