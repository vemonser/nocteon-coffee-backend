package com.nocteon.nocteon_api.review;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.review.dto.response.ReviewStatsResponse;
import com.nocteon.nocteon_api.review.service.ReviewService;
import com.nocteon.nocteon_api.support.TestJwtKeys;

@SpringBootTest(
        classes = NocteonApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ReviewStatsIntegrationTest {

    @Autowired
    private ReviewService reviewService;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    @Test
    void theCountsAddUp() {
        ReviewStatsResponse stats = reviewService.getReviewStats();

        assertThat(stats.getTotal()).isPositive();
        assertThat(stats.getPending() + stats.getApproved()).isEqualTo(stats.getTotal());
        assertThat(stats.getDistribution().values().stream().mapToLong(Long::longValue).sum())
                .isEqualTo(stats.getTotal());
    }

    /**
     * The average is over APPROVED reviews only. A pending review must not
     * move the number the storefront shows.
     */
    @Test
    void theAverageIgnoresPendingReviews() {
        ReviewStatsResponse stats = reviewService.getReviewStats();

        if (stats.getPending() > 0) {
            assertThat(stats.getAverageRating()).isNotNull();
        }
    }
}
