package com.nocteon.nocteon_api.journal.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;

import com.nocteon.nocteon_api.auth.security.UserPrincipal;
import com.nocteon.nocteon_api.common.dto.ApiResponse;
import com.nocteon.nocteon_api.common.dto.LookupFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.journal.dto.request.JournalFilterRequest;
import com.nocteon.nocteon_api.journal.dto.request.JournalPostRequest;
import com.nocteon.nocteon_api.journal.dto.response.JournalCategoryResponseDashboard;
import com.nocteon.nocteon_api.journal.dto.response.JournalPostResponse;
import com.nocteon.nocteon_api.journal.dto.response.JournalPostResponseDashboard;
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
                        @Valid
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
                        @Valid
                        @ModelAttribute JournalFilterRequest filter) {
                return ResponseEntity.ok(
                                ApiResponse.success(journalService.getAllDashboard(filter), "Posts retrieved"));
        }

        @PostMapping(value = "/dashboard/journal/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @PreAuthorize("hasAnyAuthority('journal:create', 'journal:update')")
        public ResponseEntity<ApiResponse<Map<String, String>>> uploadInlineImage(
                        @RequestPart("file") org.springframework.web.multipart.MultipartFile file) {
                return ResponseEntity.ok(ApiResponse.success(
                                Map.of("url", journalService.uploadInlineImage(file)),
                                "Image uploaded"));
        }

        @GetMapping("/dashboard/journal-categories")
        @PreAuthorize("hasAuthority('journal:read')")
        public ResponseEntity<ApiResponse<List<JournalCategoryResponseDashboard>>> getCategories() {
                return ResponseEntity.ok(
                                ApiResponse.success(journalService.getCategories(), "Categories retrieved"));
        }

        @GetMapping("/dashboard/journal/{slug}")
        @PreAuthorize("hasAuthority('journal:read')")
        public ResponseEntity<ApiResponse<JournalPostResponseDashboard>> getDashboardBySlug(@PathVariable String slug) {
                return ResponseEntity.ok(
                                ApiResponse.success(journalService.getDashboardBySlug(slug), "Post retrieved"));
        }

        @PostMapping(value = "/dashboard/journal", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @PreAuthorize("hasAuthority('journal:create')")
        public ResponseEntity<ApiResponse<JournalPostResponseDashboard>> create(
                        @Valid @RequestPart("data") JournalPostRequest request,
                        @RequestPart(value = "image", required = false) MultipartFile image,
                        @AuthenticationPrincipal UserPrincipal principal) {
                return ResponseEntity.status(HttpStatus.CREATED)
                                .body(ApiResponse.success(
                                                journalService.create(request, image, principal.getUserId()),
                                                "Post created"));
        }

        @PutMapping(value = "/dashboard/journal/{slug}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @PreAuthorize("hasAuthority('journal:update')")
        public ResponseEntity<ApiResponse<JournalPostResponseDashboard>> update(
                        @PathVariable String slug,
                        @Valid @RequestPart("data") JournalPostRequest request,
                        @RequestPart(value = "image", required = false) MultipartFile image) {
                return ResponseEntity.ok(
                                ApiResponse.success(journalService.update(slug, request, image), "Post updated"));
        }

        @PostMapping("/dashboard/journal/{slug}/cover")
        @PreAuthorize("hasAuthority('journal:update')")
        public ResponseEntity<ApiResponse<JournalPostResponseDashboard>> uploadCover(
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

        /**
         * Trashed posts.
         *
         * <p>Declared before {@code /dashboard/journal/{slug}}-style routes would matter only if a
         * path variable shared the name; {@code deleted} is a distinct literal here and every
         * sibling write endpoint is keyed by slug, so there is no ambiguity to design around.
         */
        @GetMapping("/dashboard/journal/deleted")
        @PreAuthorize("hasAuthority('journal:delete')")
        public ResponseEntity<ApiResponse<PageResponse<JournalPostResponseDashboard>>> getAllDeleted(
                        @Valid
                        @ModelAttribute LookupFilterRequest filter) {
                return ResponseEntity.ok(
                                ApiResponse.success(
                                                journalService.getAllDeleted(filter),
                                                "Deleted posts retrieved"));
        }

        /**
         * Un-delete a post, by id.
         *
         * <p>Keyed by id rather than slug because that is what {@code @SQLRestriction} hides from
         * every ordinary query: a trashed row cannot be found by its slug through the entity
         * manager, so the restore lookup is a native query that bypasses the restriction.
         */
        @PatchMapping("/dashboard/journal/{id}/restore")
        @PreAuthorize("hasAuthority('journal:delete')")
        public ResponseEntity<ApiResponse<Void>> restore(@PathVariable Long id) {
                journalService.restore(id);
                return ResponseEntity.ok(ApiResponse.success(null, "Post restored"));
        }

        @GetMapping("/dashboard/journal-categories/deleted")
        @PreAuthorize("hasAuthority('journal:delete')")
        public ResponseEntity<ApiResponse<PageResponse<JournalCategoryResponseDashboard>>> getAllDeletedCategories(
                        @Valid
                        @ModelAttribute LookupFilterRequest filter) {
                return ResponseEntity.ok(
                                ApiResponse.success(
                                                journalService.getAllDeletedCategories(filter),
                                                "Deleted journal categories retrieved"));
        }

        @PatchMapping("/dashboard/journal-categories/{id}/restore")
        @PreAuthorize("hasAuthority('journal:delete')")
        public ResponseEntity<ApiResponse<Void>> restoreCategory(@PathVariable Long id) {
                journalService.restoreCategory(id);
                return ResponseEntity.ok(ApiResponse.success(null, "Journal category restored"));
        }

        /**
         * Permanently deletes a trashed Post permanently deleted. The row must already be soft-deleted — restoring
         * is still possible until this is called, and after it nothing is recoverable. Cloudinary
         * assets are removed here and only here; the soft delete keeps them so a restore brings
         * the item back intact.
         */
        @DeleteMapping("/dashboard/journal/{id}/permanent")
        @PreAuthorize("hasAuthority('journal:delete')")
        public ResponseEntity<Void> permanentDelete(@PathVariable Long id) {
                journalService.permanentDelete(id);
                return ResponseEntity.noContent().build();
        }

        /**
         * Permanently deletes a trashed journal category. Same trash-only contract as the post
         * endpoint above.
         */
        @DeleteMapping("/dashboard/journal-categories/{id}/permanent")
        @PreAuthorize("hasAuthority('journal:delete')")
        public ResponseEntity<Void> permanentDeleteCategory(@PathVariable Long id) {
                journalService.permanentDeleteCategory(id);
                return ResponseEntity.noContent().build();
        }
}
