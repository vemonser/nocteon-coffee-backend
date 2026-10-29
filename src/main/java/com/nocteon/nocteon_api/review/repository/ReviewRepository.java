package com.nocteon.nocteon_api.review.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nocteon.nocteon_api.review.entity.Review;
import com.nocteon.nocteon_api.review.entity.ReviewStatus;

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
                        AND r.status = 'APPROVED'
                        ORDER BY r.createdAt DESC
                        """)
        Page<Review> findByProductSlug(
                        @Param("slug") String slug,
                        Pageable pageable);

        /**
         * NEW — dashboard moderation queue. Distinct from findByProductSlug:
         * this one is for admins and intentionally does NOT filter by
         * isApproved, so the dashboard can show pending + approved + filter
         * between them via the :status param (null = show both).
         *
         * <p>The search is a ready-made PATTERN ("%term%"), lower-cased by the
         * caller: the columns are compared with LOWER(), and the pattern is
         * not, because LOWER() around a concatenation makes PostgreSQL infer
         * the parameter's type — and it picks bytea, which has no lower().
         *
         * <p>The date range is a pair of INSTANTS that are NEVER null: a
         * parameter that appears in an "is null" test has no type there, and
         * PostgreSQL then cannot infer it. An absent bound is a wide-open
         * one (see ReviewService.ANY_TIME_FROM / ANY_TIME_TO).
         */
        @Query("""
                            SELECT r
                            FROM Review r
                            LEFT JOIN r.product p
                            LEFT JOIN r.user u
                            WHERE r.deletedAt IS NULL
                            AND (CAST(:productSlug AS text) IS NULL OR LOWER(p.slug) = LOWER(CAST(:productSlug AS text)))
                            AND (CAST(:search AS text) IS NULL OR
                                LOWER(p.slug) LIKE CAST(:search AS text)
                                OR LOWER(u.username) LIKE CAST(:search AS text)
                                OR LOWER(r.comment) LIKE CAST(:search AS text))
                              AND (:status IS NULL OR r.status = :status)
                              AND (:isVerified IS NULL OR r.isVerified = :isVerified)
                              AND (:minRating IS NULL OR r.rating >= :minRating)
                              AND r.createdAt >= :createdFrom
                              AND r.createdAt <= :createdTo
                            ORDER BY r.createdAt DESC
                        """)
        Page<Review> findAllDashboard(
                        @Param("productSlug") String productSlug,
                        @Param("search") String search,
                        @Param("status") ReviewStatus status,
                        @Param("isVerified") Boolean isVerified,
                        @Param("minRating") Integer minRating,
                        @Param("createdFrom") java.time.Instant createdFrom,
                        @Param("createdTo") java.time.Instant createdTo,
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
                              AND r.status = 'APPROVED'
                        """)
        Double findAverageRatingByProductSlug(
                        @Param("slug") String slug);

        @Query("""
                            SELECT COUNT(r) FROM Review r
                            WHERE r.product.slug = :slug
                            AND r.deletedAt IS NULL
                            AND r.status = 'APPROVED'
                        """)
        Long countByProductSlug(@Param("slug") String slug);

        /**
         * The rating aggregate for a WHOLE page of products, in one statement.
         *
         * <p>The predicate is the same "counted review" definition the product
         * page uses ({@link #findAverageRatingByProductSlug} and
         * {@link #countByProductSlug}): not soft-deleted, and APPROVED. The
         * dashboard column and the reviews page must never disagree about
         * which reviews exist, so the three queries stay word-for-word
         * identical — if the definition moves, it moves in all three.
         *
         * <p>Grouping by product is what keeps the products list at ONE extra
         * statement per page: a per-row call would be an N+1, and the list is
         * paginated (see {@code DashboardListQueryBudgetTest}).
         *
         * @return rows of {@code [productId, averageRating, reviewCount]};
         *         products with no counted review are simply absent
         */
        @Query("""
                            SELECT r.product.id, AVG(r.rating), COUNT(r)
                            FROM Review r
                            WHERE r.product.id IN :productIds
                              AND r.deletedAt IS NULL
                              AND r.status = 'APPROVED'
                            GROUP BY r.product.id
                        """)
        List<Object[]> findApprovedRatingAggregatesByProductIds(
                        @Param("productIds") Collection<Long> productIds);

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

        Long countByStatus(ReviewStatus status);

        long countByDeletedAtIsNull();

        Long countByDeletedAtIsNullAndStatus(ReviewStatus status);

        Long countByDeletedAtIsNullAndIsVerifiedTrue();

        @Query("""
                            SELECT AVG(r.rating) FROM Review r
                            WHERE r.deletedAt IS NULL
                            AND r.status = 'APPROVED'
                        """)
        Double findAverageRatingApproved();

        @Query("""
                            SELECT AVG(r.rating) FROM Review r
                            WHERE r.deletedAt IS NULL
                        """)
        Double findAverageRatingAll();

        @Query("""
                            SELECT r.rating, COUNT(r) FROM Review r
                            WHERE r.deletedAt IS NULL
                            GROUP BY r.rating
                        """)
        java.util.List<Object[]> findRatingDistribution();
}