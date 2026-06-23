package com.nocteon.nocteon_api.wishlist.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nocteon.nocteon_api.wishlist.entity.WishlistItem;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {
    Optional<WishlistItem> findByWishlistIdAndProductSlug(Long wishlistId, String productSlug);
    boolean existsByWishlistIdAndProductSlug(Long wishlistId, String productSlug);
}