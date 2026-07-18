package com.nocteon.nocteon_api.review.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import com.nocteon.nocteon_api.auth.security.UserPrincipal;
import com.nocteon.nocteon_api.common.dto.ApiResponse;
import com.nocteon.nocteon_api.common.dto.BaseFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.review.dto.request.ReviewRequest;
import com.nocteon.nocteon_api.review.dto.response.ReviewResponse;
import com.nocteon.nocteon_api.review.service.ReviewService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@Validated
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping("/products/{slug}/reviews")
    public ResponseEntity<ApiResponse<PageResponse<ReviewResponse>>> getProductReviews(
            @PathVariable String slug,
            @ModelAttribute BaseFilterRequest filter) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        reviewService.getProductReviews(slug, filter),
                        "Reviews retrieved"));
    }

    @PostMapping("/products/reviews")
    @PreAuthorize("hasAuthority('review:create')")
    public ResponseEntity<ApiResponse<ReviewResponse>> create(
            @Valid @RequestBody ReviewRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        reviewService.create(request, principal),
                        "Review created"));
    }

    @DeleteMapping("/products/reviews/{id}")
    @PreAuthorize("hasAuthority('review:delete')")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        reviewService.delete(id, principal);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/dashboard/reviews")
    @PreAuthorize("hasAuthority('review:moderate')")
    public ResponseEntity<ApiResponse<PageResponse<ReviewResponse>>> getDashboardReviews(
            @RequestParam(required = false) String productSlug,
            @RequestParam(required = false) Boolean isApproved,
            @RequestParam(required = false) Boolean isVerified,
            @RequestParam(required = false) Integer minRating,
            @ModelAttribute BaseFilterRequest filter) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        reviewService.getDashboardReviews(
                                productSlug, isApproved, isVerified, minRating, filter),
                        "Dashboard reviews retrieved"));
    }

    @GetMapping("/dashboard/reviews/{id}")
    @PreAuthorize("hasAuthority('review:moderate')")
    public ResponseEntity<ApiResponse<ReviewResponse>> getDashboardById(@PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        reviewService.getDashboardById(id),
                        "Review retrieved"));
    }

    @PatchMapping("/dashboard/reviews/{id}/approval")
    @PreAuthorize("hasAuthority('review:moderate')")
    public ResponseEntity<ApiResponse<ReviewResponse>> setApproval(
            @PathVariable Long id,
            @RequestParam boolean approved) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        reviewService.setApproval(id, approved),
                        approved ? "Review approved" : "Review unapproved"));
    }

    @DeleteMapping("/dashboard/reviews/{id}")
    @PreAuthorize("hasAuthority('review:delete')")
    public ResponseEntity<Void> adminDelete(@PathVariable Long id) {
        reviewService.adminDelete(id);
        return ResponseEntity.noContent().build();
    }
}