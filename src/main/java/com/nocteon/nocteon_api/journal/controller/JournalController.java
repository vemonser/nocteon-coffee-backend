package com.nocteon.nocteon_api.journal.controller;

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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;

import com.nocteon.nocteon_api.common.dto.ApiResponse;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.journal.dto.request.JournalFilterRequest;
import com.nocteon.nocteon_api.journal.dto.request.JournalPostRequest;
import com.nocteon.nocteon_api.journal.dto.response.JournalPostResponse;
import com.nocteon.nocteon_api.journal.service.JournalService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@Validated
@RequiredArgsConstructor
public class JournalController {

        private final JournalService journalService;

        @GetMapping("/journal")
        public ResponseEntity<ApiResponse<PageResponse<JournalPostResponse>>> getAll(
                        @ModelAttribute JournalFilterRequest filter) {
                return ResponseEntity.ok(
                                ApiResponse.success(journalService.getAll(filter), "Posts retrieved"));
        }

        @GetMapping("/journal/{slug}")
        public ResponseEntity<ApiResponse<JournalPostResponse>> getBySlug(
                        @PathVariable String slug) {
                return ResponseEntity.ok(
                                ApiResponse.success(journalService.getBySlug(slug), "Post retrieved"));
        }

        @GetMapping("/dashboard/journal")
        @PreAuthorize("hasAuthority('journal:read')")
        public ResponseEntity<ApiResponse<PageResponse<JournalPostResponse>>> getAllDashboard(
                        @ModelAttribute JournalFilterRequest filter) {
                return ResponseEntity.ok(
                                ApiResponse.success(journalService.getAllDashboard(filter), "Posts retrieved"));
        }

        @PostMapping(value = "/dashboard/journal", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @PreAuthorize("hasAuthority('journal:create')")
        public ResponseEntity<ApiResponse<JournalPostResponse>> create(
                        @Valid @RequestPart("data") JournalPostRequest request,
                        @RequestPart(value = "image", required = false) MultipartFile image) {
                return ResponseEntity.status(HttpStatus.CREATED)
                                .body(ApiResponse.success(journalService.create(request, image), "Post created"));
        }

        @PutMapping(value = "/dashboard/journal/{slug}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @PreAuthorize("hasAuthority('journal:update')")
        public ResponseEntity<ApiResponse<JournalPostResponse>> update(
                        @PathVariable String slug,
                        @Valid @RequestPart("data") JournalPostRequest request,
                        @RequestPart(value = "image", required = false) MultipartFile image) {
                return ResponseEntity.ok(
                                ApiResponse.success(journalService.update(slug, request, image), "Post updated"));
        }

        @PostMapping("/dashboard/journal/{slug}/cover")
        @PreAuthorize("hasAuthority('journal:update')")
        public ResponseEntity<ApiResponse<JournalPostResponse>> uploadCover(
                        @PathVariable String slug,
                        @RequestParam("file") MultipartFile file) {
                return ResponseEntity.ok(
                                ApiResponse.success(journalService.uploadCover(slug, file), "Cover uploaded"));
        }

        @DeleteMapping("/dashboard/journal/{slug}")
        @PreAuthorize("hasAuthority('journal:delete')")
        public ResponseEntity<Void> delete(@PathVariable String slug) {
                journalService.delete(slug);
                return ResponseEntity.noContent().build();
        }
}