package com.nocteon.nocteon_api.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.auth.entity.User;
import com.nocteon.nocteon_api.auth.repository.UserRepository;
import com.nocteon.nocteon_api.category.entity.Category;
import com.nocteon.nocteon_api.category.repository.CategoryRepository;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.product.dto.request.ProductFilterRequest;
import com.nocteon.nocteon_api.product.dto.response.ProductCardResponse;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductTranslation;
import com.nocteon.nocteon_api.product.entity.ProductVariant;
import com.nocteon.nocteon_api.product.enums.ProductType;
import com.nocteon.nocteon_api.product.repository.ProductRepository;
import com.nocteon.nocteon_api.product.service.ProductService;
import com.nocteon.nocteon_api.review.entity.Review;
import com.nocteon.nocteon_api.review.entity.ReviewStatus;
import com.nocteon.nocteon_api.review.repository.ReviewRepository;
import com.nocteon.nocteon_api.review.service.ReviewService;
import com.nocteon.nocteon_api.support.TestJwtKeys;

import jakarta.persistence.EntityManager;

/**
 * FE-14: the rating aggregate on the dashboard product list.
 *
 * <p>The dashboard column and the reviews moderation page must never disagree
 * about which reviews exist, so the aggregate reuses the reviews page's
 * definition of a counted review (not soft-deleted, APPROVED). This test pins
 * the three facts a reader of the column depends on:
 *
 * <ol>
 *   <li>a pending or soft-deleted review is not counted and does not move the
 *       average;</li>
 *   <li>a product nobody reviewed reports {@code (null, 0)} — not an average
 *       of zero, which would claim five one-star ratings exist;</li>
 *   <li>the average carries one decimal, the precision the column prints.</li>
 * </ol>
 */
@SpringBootTest(classes = NocteonApiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class ProductRatingAggregateIntegrationTest {

    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ReviewRepository reviewRepository;
    @Autowired
    private ReviewService reviewService;
    @Autowired
    private ProductService productService;
    @Autowired
    private EntityManager entityManager;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    @Test
    void approvedReviewsAreCountedAndEverythingElseIsNot() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Category category = persistCategory("fe14-" + suffix);
        Product reviewed = persistProduct(category, "rated-" + suffix);
        Product unreviewed = persistProduct(category, "unrated-" + suffix);

        // 5 and 4 approved, plus a 1-star review that is still pending and a
        // soft-deleted 1-star one: neither may reach the column.
        persistReview(reviewed, suffix + "-a", 5, ReviewStatus.APPROVED);
        persistReview(reviewed, suffix + "-b", 4, ReviewStatus.APPROVED);
        persistReview(reviewed, suffix + "-c", 1, ReviewStatus.PENDING);
        Review deleted = persistReview(reviewed, suffix + "-d", 1, ReviewStatus.APPROVED);
        deleted.softDelete();
        reviewRepository.save(deleted);
        entityManager.flush();

        Map<Long, ReviewService.RatingSummary> summaries = reviewService.ratingSummariesByProductIds(
                List.of(reviewed.getId(), unreviewed.getId()));

        ReviewService.RatingSummary rated = summaries.get(reviewed.getId());
        assertThat(rated.reviewCount()).isEqualTo(2);
        assertThat(rated.averageRating()).isEqualTo(4.5);

        ReviewService.RatingSummary unrated = summaries.get(unreviewed.getId());
        assertThat(unrated.reviewCount()).isZero();
        assertThat(unrated.averageRating()).isNull();

        // The definition matches the reviews page's own aggregate exactly: the
        // average and the count come from the same rows.
        assertThat(reviewService.ratingSummariesByProductIds(List.of(reviewed.getId()))
                .get(reviewed.getId()).averageRating())
                .isEqualTo(reviewRepository.findAverageRatingByProductSlug(reviewed.getSlug()));
        assertThat(rated.reviewCount().longValue())
                .isEqualTo(reviewRepository.countByProductSlug(reviewed.getSlug()));
    }

    @Test
    void theAverageCarriesOneDecimal() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Category category = persistCategory("fe14-rounding-" + suffix);
        Product product = persistProduct(category, "rounding-" + suffix);

        // (5 + 4 + 4) / 3 = 4.333... -> 4.3, the value the column prints.
        persistReview(product, suffix + "-a", 5, ReviewStatus.APPROVED);
        persistReview(product, suffix + "-b", 4, ReviewStatus.APPROVED);
        persistReview(product, suffix + "-c", 4, ReviewStatus.APPROVED);
        entityManager.flush();

        ReviewService.RatingSummary summary = reviewService
                .ratingSummariesByProductIds(List.of(product.getId()))
                .get(product.getId());

        assertThat(summary.averageRating()).isEqualTo(4.3);
        assertThat(summary.reviewCount()).isEqualTo(3);
    }

    /**
     * The end-to-end path the dashboard actually calls: the card the list
     * serializes carries both fields, and a product with no reviews carries
     * {@code null} + {@code 0} instead of a fabricated zero average.
     */
    @Test
    void theDashboardCardCarriesTheRatingAndTheCount() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Category category = persistCategory("fe14-card-" + suffix);
        Product reviewed = persistProduct(category, "card-rated-" + suffix);
        Product unreviewed = persistProduct(category, "card-unrated-" + suffix);

        persistReview(reviewed, suffix + "-a", 5, ReviewStatus.APPROVED);
        persistReview(reviewed, suffix + "-b", 4, ReviewStatus.APPROVED);
        persistReview(reviewed, suffix + "-c", 2, ReviewStatus.PENDING);
        entityManager.flush();

        ProductFilterRequest filter = new ProductFilterRequest();
        filter.setSearch(suffix);
        filter.setSize(20);
        PageResponse<ProductCardResponse> page = productService.getAllDashboard(filter);

        ProductCardResponse ratedCard = card(page, reviewed.getSlug());
        assertThat(ratedCard.getAverageRating()).isEqualTo(4.5);
        assertThat(ratedCard.getReviewCount()).isEqualTo(2);

        ProductCardResponse unratedCard = card(page, unreviewed.getSlug());
        assertThat(unratedCard.getAverageRating()).isNull();
        assertThat(unratedCard.getReviewCount()).isZero();
    }

    private static ProductCardResponse card(PageResponse<ProductCardResponse> page, String slug) {
        return page.getContent().stream()
                .filter(card -> slug.equals(card.getSlug()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("product " + slug + " was not on the page"));
    }

    private Category persistCategory(String slug) {
        Category category = Category.builder().slug(slug).isActive(true).build();
        categoryRepository.save(category);
        return category;
    }

    private Product persistProduct(Category category, String slug) {
        Product product = Product.builder()
                .slug(slug)
                .category(category)
                .productType(ProductType.COFFEE)
                .isActive(true)
                .build();
        product.getTranslations().add(ProductTranslation.builder()
                .product(product).language("en").name(slug).build());
        product.getVariants().add(ProductVariant.builder()
                .product(product)
                .sku("SKU-" + slug)
                .price(new BigDecimal("650.00"))
                .stockQuantity(10)
                .isActive(true)
                .version(0L)
                .build());
        productRepository.save(product);
        entityManager.flush();
        return product;
    }

    /**
     * One review per reviewer (the table is unique on user + product), so every
     * call creates its own reviewer.
     */
    private Review persistReview(Product product, String handle, int rating, ReviewStatus status) {
        User user = userRepository.save(User.builder()
                .username("fe14-" + handle)
                .email("fe14-" + handle + "@example.test")
                .role("CUSTOMER")
                .provider(com.nocteon.nocteon_api.auth.enums.Provider.LOCAL)
                .enabled(true)
                .isActive(true)
                .build());
        return reviewRepository.save(Review.builder()
                .product(product)
                .user(user)
                .rating(rating)
                .comment("FE-14 test review")
                .isVerified(false)
                .status(status)
                .build());
    }
}
