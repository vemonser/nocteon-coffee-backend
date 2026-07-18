package com.nocteon.nocteon_api.review.service;

import java.util.List;
import java.util.Objects;

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

        public PageResponse<ReviewResponse> getProductReviews(
                        String productSlug, BaseFilterRequest filter) {
                Page<Review> page = reviewRepository.findByProductSlug(
                                productSlug, filter.toPageable());
                return PageResponse.of(page.map(this::buildResponse));
        }

        public PageResponse<ReviewResponse> getDashboardReviews(
                        String productSlug, Boolean isApproved, Boolean isVerified,
                        Integer minRating, BaseFilterRequest filter) {
                String search = Objects.requireNonNullElse(
                                productSlug,
                                "");

                Page<Review> page = reviewRepository.findAllDashboard(
                                search, isApproved, isVerified, minRating, filter.toPageable());
                return PageResponse.of(page.map(this::buildResponse));
        }

        public ReviewResponse getDashboardById(Long id) {
                Review review = reviewRepository.findById(id)
                                .orElseThrow(ReviewNotFoundException::new);
                if (review.getDeletedAt() != null) {
                        throw new ReviewNotFoundException();
                }
                return buildResponse(review);
        }

        @Transactional
        public ReviewResponse setApproval(Long reviewId, boolean approved) {
                Review review = reviewRepository.findById(reviewId)
                                .orElseThrow(ReviewNotFoundException::new);

                if (review.getDeletedAt() != null) {
                        throw new ReviewNotFoundException();
                }

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
                List<ProductTranslation> productTranslations = product.getTranslations();
                ProductTranslation productTranslation = productTranslations.stream()
                                .filter(t -> t.getLanguage().equals(language))
                                .findFirst()
                                .orElse(productTranslations.isEmpty() ? null : productTranslations.get(0));

                ProductMedia primaryMedia = product.getMedia().stream()
                                .filter(ProductMedia::isPrimary)
                                .findFirst()
                                .orElse(product.getMedia().isEmpty() ? null : product.getMedia().get(0));

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
