package com.nocteon.nocteon_api.wishlist.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import com.nocteon.nocteon_api.auth.security.UserPrincipal;
import com.nocteon.nocteon_api.common.dto.ApiResponse;
import com.nocteon.nocteon_api.wishlist.dto.response.WishlistResponse;
import com.nocteon.nocteon_api.wishlist.service.WishlistService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/wishlist")
@Validated
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistService wishlistService;

    @GetMapping
    @PreAuthorize("hasAuthority('wishlist:manage')")
    public ResponseEntity<ApiResponse<WishlistResponse>> getWishlist(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(
                ApiResponse.success(wishlistService.getWishlist(principal), "Wishlist retrieved"));
    }

    @PostMapping("/{productSlug}")
    @PreAuthorize("hasAuthority('wishlist:manage')")
    public ResponseEntity<ApiResponse<WishlistResponse>> addItem(
            @PathVariable String productSlug,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        wishlistService.addItem(productSlug, principal),
                        "Product added to wishlist"));
    }

    @DeleteMapping("/{productSlug}")
    @PreAuthorize("hasAuthority('wishlist:manage')")
    public ResponseEntity<ApiResponse<WishlistResponse>> removeItem(
            @PathVariable String productSlug,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        wishlistService.removeItem(productSlug, principal),
                        "Product removed from wishlist"));
    }
}