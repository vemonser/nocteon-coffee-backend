package com.nocteon.nocteon_api.review.service;

import java.util.HashMap;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Objects;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import com.nocteon.nocteon_api.auth.entity.User;
import com.nocteon.nocteon_api.auth.entity.UserProfile;
import com.nocteon.nocteon_api.auth.security.UserPrincipal;
import com.nocteon.nocteon_api.common.dto.BaseFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.common.exception.notFound.ProductNotFoundException;
import com.nocteon.nocteon_api.common.exception.notFound.ReviewNotFoundException;
import com.nocteon.nocteon_api.common.exception.product.DuplicateReviewException;
import com.nocteon.nocteon_api.notifications.event.ReviewCreatedEvent;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductMedia;
import com.nocteon.nocteon_api.product.entity.ProductTranslation;
import com.nocteon.nocteon_api.order.repository.OrderRepository;
import com.nocteon.nocteon_api.product.repository.ProductRepository;
import com.nocteon.nocteon_api.review.dto.request.ReviewRequest;
import com.nocteon.nocteon_api.review.dto.response.ReviewResponse;
import com.nocteon.nocteon_api.review.dto.response.ReviewStatsResponse;
import com.nocteon.nocteon_api.review.entity.Review;
import com.nocteon.nocteon_api.review.entity.ReviewStatus;
import com.nocteon.nocteon_api.review.repository.ReviewRepository;
import com.nocteon.nocteon_api.common.repository.SearchPatterns;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewService {
        private final ReviewRepository reviewRepository;
        private final ProductRepository productRepository;
        private final ApplicationEventPublisher eventPublisher;
        private final OrderRepository orderRepository;

        @Transactional
        public ReviewResponse create(ReviewRequest request, UserPrincipal principal) {
                Product product = productRepository
                                .findBySlugAndLanguage(
                                                request.getProductSlug(),
                                                LocaleContextHolder.getLocale().getLanguage())
                                .orElseThrow(ProductNotFoundException::new);

                if (reviewRepository.existsByUserIdAndProductSlug(
                                principal.getUserId(), request.getProductSlug())) {
                        throw new DuplicateReviewException();
                }

                boolean hasPurchased = reviewRepository.hasUserPurchasedProduct(
                                principal.getUserId(), request.getProductSlug());

                Review review = Review.builder()
                                .user(User.builder().id(principal.getUserId()).build())
                                .product(product)
                                .rating(request.getRating())
                                .comment(request.getComment())
                                .isVerified(hasPurchased)
                                .status(ReviewStatus.PENDING)
                                .build();

                review = reviewRepository.save(review);
                log.info("Review created by user {} for product {}",
                                principal.getUserId(), request.getProductSlug());
                eventPublisher.publishEvent(new ReviewCreatedEvent(review.getId(), review.getProduct().getSlug()));

                return buildResponse(review);
        }

        @Transactional(readOnly = true)
        public PageResponse<ReviewResponse> getProductReviews(
                        String productSlug, BaseFilterRequest filter) {
                Page<Review> page = reviewRepository.findByProductSlug(
                                productSlug, filter.toPageable());
                return PageResponse.of(page.map(this::buildResponse));
        }

        @Transactional(readOnly = true)
        public PageResponse<ReviewResponse> getDashboardReviews(
                        String productSlug, String search, Boolean isApproved, Boolean isVerified,
                        Integer minRating, java.time.Instant createdFrom, java.time.Instant createdTo,
                        BaseFilterRequest filter) {

                ReviewStatus status = null;
                if (Boolean.TRUE.equals(isApproved)) {
                        status = ReviewStatus.APPROVED;
                } else if (Boolean.FALSE.equals(isApproved)) {
                        status = ReviewStatus.PENDING;
                } else if (isApproved == null && (search == null || search.isBlank())) {
                        // When neither isApproved nor search is provided, default to showing
                        // pending and approved (not rejected) for the main moderation queue.
                        // This is a UI-driven default; rejected reviews are available when
                        // the filter is explicitly set to REJECTED.
                }

                Page<Review> page = reviewRepository.findAllDashboard(
                                productSlug, SearchPatterns.like(search), status, isVerified, minRating,
                                SearchPatterns.from(createdFrom), SearchPatterns.to(createdTo), filter.toPageable());

                // The order behind a verified review is attached HERE, so the
                // check mark the dashboard shows next to the reviewer's name
                // is a promise the link keeps. ONE query for the whole page.
                Map<String, OrderRef> refs = loadOrderRefs(page.getContent());
                return PageResponse.of(page.map(review -> buildResponse(review, refs)));
        }

        /**
         * The search term as a ready-made LIKE pattern, lower-cased.
         *
         * <p>The repository compares it with LOWER(column) LIKE :search, so
         * the pattern must already be lower-cased. Wrapping the pattern in
         * LOWER() instead would make PostgreSQL infer the parameter's type
         * as bytea — which has no lower(), and the query fails.
         */
        /**
         * The widest range the query accepts, used when the filter has no
         * bound.
         *
         * <p>A parameter that appears in an "is null" test has no type there,
         * and PostgreSQL then cannot infer the type of the parameter
         * ("could not determine data type of parameter"). So the bounds are
         * never null: an absent filter is a range that covers everything.
         */
        static final Instant ANY_TIME_FROM = Instant.parse("1970-01-01T00:00:00Z");
        static final Instant ANY_TIME_TO = Instant.parse("2100-01-01T00:00:00Z");

        private static Instant from(Instant bound) {
                return bound == null ? ANY_TIME_FROM : bound;
        }

        private static Instant to(Instant bound) {
                return bound == null ? ANY_TIME_TO : bound;
        }

	@Transactional(readOnly = true)
	public ReviewStatsResponse getDashboardStats() {
		long total = reviewRepository.countByDeletedAtIsNull();
		long pending = reviewRepository.countByDeletedAtIsNullAndStatus(ReviewStatus.PENDING);
		long approved = reviewRepository.countByDeletedAtIsNullAndStatus(ReviewStatus.APPROVED);
		long rejected = reviewRepository.countByDeletedAtIsNullAndStatus(ReviewStatus.REJECTED);
		long verified = reviewRepository.countByDeletedAtIsNullAndIsVerifiedTrue();
		Double avg = reviewRepository.findAverageRatingApproved();
		Double avgAll = reviewRepository.findAverageRatingAll();

		Map<String, Long> distribution = new HashMap<>();
		for (int i = 1; i <= 5; i++) distribution.put(String.valueOf(i), 0L);
		for (Object[] row : reviewRepository.findRatingDistribution()) {
			Integer rating = (Integer) row[0];
			Long count = (Long) row[1];
			if (rating != null && rating >= 1 && rating <= 5) {
					distribution.put(String.valueOf(rating), count);
			}
		}

		return ReviewStatsResponse.builder()
					.total(total)
					.pending(pending)
					.approved(approved)
					.rejected(rejected)
					.verified(verified)
					.averageRating(avg)
					.averageRatingAll(avgAll)
					.distribution(distribution)
					.build();
	}

        /**
         * The moderation statistics.
         *
         * <p>Every star bucket is present in the distribution, including the
         * empty ones: the dashboard draws a bar for each, and a missing key
         * would read as "no data" rather than "nobody gave that rating".
         */
        @Transactional(readOnly = true)
        public ReviewStatsResponse getReviewStats() {
                Map<String, Long> distribution = new LinkedHashMap<>();
                for (int star = 1; star <= 5; star++) {
                        distribution.put(String.valueOf(star), 0L);
                }
                for (Object[] row : reviewRepository.findRatingDistribution()) {
                        distribution.put(String.valueOf(((Number) row[0]).intValue()),
                                ((Number) row[1]).longValue());
                }

                long total = reviewRepository.countByDeletedAtIsNull();
                long pending = reviewRepository.countByDeletedAtIsNullAndStatus(ReviewStatus.PENDING);
                long approved = reviewRepository.countByDeletedAtIsNullAndStatus(ReviewStatus.APPROVED);

                return ReviewStatsResponse.builder()
                                .total(total)
                                .pending(pending)
                                .approved(approved)
                                .averageRating(reviewRepository.findAverageRatingApproved())
                                .distribution(distribution)
                                .build();
        }
        @Transactional(readOnly = true)
        public ReviewResponse getDashboardById(Long id) {
                Review review = reviewRepository.findById(id)
                                .orElseThrow(ReviewNotFoundException::new);
                return buildResponse(review);
        }


        /**
         * Approve or unapprove a set of reviews in one call.
         *
         * <p>A review that was deleted between the list rendering and the
         * click is not an error — it is simply not there any more, and the
         * count reports what ACTUALLY changed.
         */
        @Transactional
        public int setApprovalBulk(List<Long> ids, boolean approved) {
                if (ids == null || ids.isEmpty()) {
                        return 0;
                }
                int updated = 0;
                for (Long id : ids) {
                        Review review = reviewRepository.findById(id).orElse(null);
                        if (review == null) {
                                continue;
                        }
                        review.setStatus(approved ? ReviewStatus.APPROVED : ReviewStatus.PENDING);
                        reviewRepository.save(review);
                        updated++;
                }
                log.info("Bulk approval: {} reviews set to approved={}", updated, approved);
                return updated;
        }
        @Transactional
        public ReviewResponse setApproval(Long reviewId, boolean approved) {
                Review review = reviewRepository.findById(reviewId)
                                .orElseThrow(ReviewNotFoundException::new);

                review.setStatus(approved ? ReviewStatus.APPROVED : ReviewStatus.PENDING);
                review = reviewRepository.save(review);
                log.info("Review {} approval set to {}", reviewId, approved);
                return buildResponse(review);
        }

        @Transactional
        public ReviewResponse setRejected(Long reviewId) {
                Review review = reviewRepository.findById(reviewId)
                                .orElseThrow(ReviewNotFoundException::new);

                review.setStatus(ReviewStatus.REJECTED);
                review = reviewRepository.save(review);
                log.info("Review {} rejected", reviewId);
                return buildResponse(review);
        }

        @Transactional
        public void delete(Long reviewId, UserPrincipal principal) {
                Review review = reviewRepository
                                .findByIdAndUserId(reviewId, principal.getUserId())
                                .orElseThrow(ReviewNotFoundException::new);
                review.softDelete();
                reviewRepository.save(review);
                log.info("Review {} deleted by user {}", reviewId, principal.getUserId());
        }

        @Transactional
        public void adminDelete(Long reviewId) {
                Review review = reviewRepository.findById(reviewId)
                                .orElseThrow(ReviewNotFoundException::new);
                review.softDelete();
                reviewRepository.save(review);
        }


        /** The order behind a verified purchase. */
        public record OrderRef(Long orderId, String orderNumber) {}

        /**
         * The order map for a page of reviews, keyed by product and user.
         *
         * <p>ONE query per page, not one per row: a page of twenty reviews
         * would otherwise cost twenty round trips to the database.
         */
        private Map<String, OrderRef> loadOrderRefs(List<Review> reviews) {
                if (reviews.isEmpty()) {
                        return Map.of();
                }
                Set<Long> userIds = reviews.stream()
                                .map(r -> r.getUser().getId())
                                .collect(Collectors.toSet());
                if (userIds.isEmpty()) {
                        return Map.of();
                }

                Map<String, OrderRef> refs = new HashMap<>();
                for (Object[] row : orderRepository.findLatestQualifyingOrdersForUsers(userIds)) {
                        long productId = ((Number) row[0]).longValue();
                        long userId = ((Number) row[1]).longValue();
                        String key = productId + ":" + userId;
                        // The rows are newest first, so the FIRST row for a
                        // pair is the one to keep.
                        refs.putIfAbsent(key, new OrderRef(
                                        ((Number) row[2]).longValue(),
                                        (String) row[3]));
                }
                return refs;
        }

        public ReviewResponse buildResponse(Review review) {
                return buildResponse(review, Map.of());
        }

        /**
         * The order is attached only when the purchase is CONFIRMED. A review
         * that is not verified carries no order, so the dashboard can trust
         * the check mark it shows next to the reviewer's name.
         */
        public ReviewResponse buildResponse(Review review, Map<String, OrderRef> orders) {
                String language = LocaleContextHolder.getLocale().getLanguage();
                UserProfile profile = review.getUser().getProfile();

                Product product = review.getProduct();
                OrderRef order = review.isVerified()
                                ? orders.get(product.getId() + ":" + review.getUser().getId())
                                : null;
                Set<ProductTranslation> productTranslations = product.getTranslations();
                ProductTranslation productTranslation = productTranslations.stream()
                                .filter(t -> t.getLanguage().equals(language))
                                .findFirst()
                                .orElse(productTranslations.isEmpty() ? null : productTranslations.stream().findFirst().orElse(null));

                ProductMedia primaryMedia = product.getMedia().stream()
                                .filter(ProductMedia::isPrimary)
                                .findFirst()
                                .orElse(product.getMedia().isEmpty() ? null : product.getMedia().stream().findFirst().orElse(null));

                return ReviewResponse.builder()
                                .id(review.getId())
                                .userId(review.getUser().getId())
                                .username(review.getUser().getUsername())
                                .userFullName(profile != null ? profile.getFullName() : null)
                                .avatarUrl(profile != null ? profile.getAvatarUrl() : null)
                                .rating(review.getRating())
                                .comment(review.getComment())
                                .verified(review.isVerified())
                                .status(review.getStatus())
                                .productSlug(review.getProduct().getSlug())
                                .primaryImageUrl(primaryMedia != null ? primaryMedia.getUrl() : null)
                                .productName(productTranslation != null ? productTranslation.getName() : null)
                                .createdAt(review.getCreatedAt())
                                .orderId(order == null ? null : order.orderId())
                                .orderNumber(order == null ? null : order.orderNumber())
                                .build();
        }
}