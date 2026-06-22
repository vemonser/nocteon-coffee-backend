package com.nocteon.nocteon_api.review.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nocteon.nocteon_api.review.entity.Review;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    @Query("""
            SELECT r FROM Review r
            LEFT JOIN FETCH r.user u
            LEFT JOIN FETCH u.profile
            WHERE r.product.slug = :slug
            AND r.deletedAt IS NULL
            ORDER BY r.createdAt DESC
            """)
    Page<Review> findByProductSlug(
            @Param("slug") String slug,
            Pageable pageable);

    @Query("""
            SELECT AVG(r.rating) FROM Review r
            WHERE r.product.slug = :slug
            AND r.deletedAt IS NULL
            """)
    Double findAverageRatingByProductSlug(@Param("slug") String slug);

    @Query("""
            SELECT COUNT(r) FROM Review r
            WHERE r.product.slug = :slug
            AND r.deletedAt IS NULL
            """)
    Long countByProductSlug(@Param("slug") String slug);

    boolean existsByUserIdAndProductSlug(Long userId, String productSlug);

    Optional<Review> findByIdAndUserId(Long id, Long userId);

    @Query("""
            SELECT CASE WHEN COUNT(o) > 0 THEN true ELSE false END
            FROM Order o
            JOIN o.items i
            WHERE o.user.id = :userId
            AND i.productVariant.product.slug = :productSlug
            AND o.status NOT IN ('CANCELLED', 'PENDING')
            """)
    boolean hasUserPurchasedProduct(
            @Param("userId") Long userId,
            @Param("productSlug") String productSlug);
}