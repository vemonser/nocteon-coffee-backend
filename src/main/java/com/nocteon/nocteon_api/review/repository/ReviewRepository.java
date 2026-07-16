package com.nocteon.nocteon_api.review.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nocteon.nocteon_api.review.entity.Review;

public interface ReviewRepository extends JpaRepository<Review, Long> {

        /**
         * CHANGED: now filters by isApproved = true as well as deletedAt IS NULL.
         * This is the PUBLIC-facing query (product page) — only approved
         * reviews should ever reach a customer.
         */
        @Query("""
                        SELECT r FROM Review r
                        LEFT JOIN r.user u
                        WHERE r.product.slug = :slug
                        AND r.deletedAt IS NULL
                        AND r.isApproved = true
                        ORDER BY r.createdAt DESC
                        """)
        Page<Review> findByProductSlug(
                        @Param("slug") String slug,
                        Pageable pageable);

        /**
         * NEW — dashboard moderation queue. Distinct from findByProductSlug:
         * this one is for admins and intentionally does NOT filter by
         * isApproved, so the dashboard can show pending + approved + filter
         * between them via the :isApproved param (null = show both).
         */
        @Query("""
                            SELECT r
                            FROM Review r
                            LEFT JOIN r.product p
                            WHERE r.deletedAt IS NULL
                            AND (:productSlug IS NULL OR
                            LOWER(p.slug) LIKE LOWER(CONCAT('%', :productSlug, '%')))
                              AND (:isApproved IS NULL OR r.isApproved = :isApproved)
                              AND (:isVerified IS NULL OR r.isVerified = :isVerified)
                              AND (:minRating IS NULL OR r.rating >= :minRating)
                            ORDER BY r.createdAt DESC
                        """)
        Page<Review> findAllDashboard(
                        @Param("productSlug") String productSlug,
                        @Param("isApproved") Boolean isApproved,
                        @Param("isVerified") Boolean isVerified,
                        @Param("minRating") Integer minRating,
                        Pageable pageable);

        /**
         * NOTE: average rating should probably only count approved reviews too,
         * otherwise a pending/rejected review could skew the public-facing
         * average before a moderator ever sees it. Added isApproved filter here.
         */
        @Query("""
                            SELECT AVG(r.rating)
                            FROM Review r
                            WHERE r.product.slug = :slug
                              AND r.deletedAt IS NULL
                              AND r.isApproved = true
                        """)
        Double findAverageRatingByProductSlug(
                        @Param("slug") String slug);

        @Query("""
                        SELECT COUNT(r) FROM Review r
                        WHERE r.product.slug = :slug
                        AND r.deletedAt IS NULL
                        AND r.isApproved = true
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