package com.nocteon.nocteon_api.review.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
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
import com.nocteon.nocteon_api.product.repository.ProductRepository;
import com.nocteon.nocteon_api.review.dto.request.ReviewRequest;
import com.nocteon.nocteon_api.review.dto.response.ReviewResponse;
import com.nocteon.nocteon_api.review.dto.response.ReviewStatsResponse;
import com.nocteon.nocteon_api.review.entity.Review;
import com.nocteon.nocteon_api.review.repository.ReviewRepository;

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
                        String productSlug, String search, Boolean isApproved,
                        Boolean isVerified, Integer minRating,
                        Instant createdFrom, Instant createdTo,
                        BaseFilterRequest filter) {
                Page<Review> page = reviewRepository.findAllDashboard(
                                productSlug, search, isApproved, isVerified, minRating,
                                createdFrom, createdTo, filter.toPageable());
                return PageResponse.of(page.map(this::buildResponse));
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
                for (Object[] row : reviewRepository.countByRating()) {
                        distribution.put(String.valueOf(((Number) row[0]).intValue()),
                                ((Number) row[1]).longValue());
                }

                long total = reviewRepository.count();
                long pending = reviewRepository.countByIsApprovedFalse();
                long approved = reviewRepository.countByIsApprovedTrue();

                return ReviewStatsResponse.builder()
                                .total(total)
                                .pending(pending)
                                .approved(approved)
                                .averageRating(reviewRepository.averageApprovedRating())
                                .distribution(distribution)
                                .build();
        }
        @Transactional(readOnly = true)
        public ReviewResponse getDashboardById(Long id) {
                Review review = reviewRepository.findById(id)
                                .orElseThrow(ReviewNotFoundException::new);
                return buildResponse(review);
        }

        @Transactional
        public ReviewResponse setApproval(Long reviewId, boolean approved) {
                Review review = reviewRepository.findById(reviewId)
                        .orElseThrow(ReviewNotFoundException::new);

                review.setApproved(approved);
                review = reviewRepository.save(review);
                log.info("Review {} approval set to {}", reviewId, approved);
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

        public ReviewResponse buildResponse(Review review) {
                String language = LocaleContextHolder.getLocale().getLanguage();
                UserProfile profile = review.getUser().getProfile();

                Product product = review.getProduct();
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
                                .username(review.getUser().getUsername())
                                .avatarUrl(profile != null ? profile.getAvatarUrl() : null)
                                .rating(review.getRating())
                                .comment(review.getComment())
                                .verified(review.isVerified())
                                .isApproved(review.isApproved())
                                .productSlug(review.getProduct().getSlug())
                                .primaryImageUrl(primaryMedia != null ? primaryMedia.getUrl() : null)
                                .productName(productTranslation != null ? productTranslation.getName() : null)
                                .createdAt(review.getCreatedAt())
                                .build();
        }
}
