package com.nocteon.nocteon_api.review;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.common.dto.BaseFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.review.dto.response.ReviewResponse;
import com.nocteon.nocteon_api.review.service.ReviewService;
import com.nocteon.nocteon_api.support.TestJwtKeys;

/**
 * The dashboard review filter, against the real database.
 *
 * <p>The product filter is EXACT, not a LIKE search: the dashboard has a
 * product dropdown, and a dropdown that matches three products because the
 * slug is a prefix of the others is worse than no dropdown at all.
 */
@SpringBootTest(
        classes = NocteonApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ReviewDashboardFilterIntegrationTest {

    @Autowired
    private ReviewService reviewService;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    @Test
    void anExactProductSlugReturnsOnlyThatProduct() {
        PageResponse<ReviewResponse> page = reviewService.getDashboardReviews(
                "brazil-mogiana", null, null, null, null, null, null, new BaseFilterRequest());

        assertThat(page.getContent()).isNotEmpty();
        assertThat(page.getContent())
                .extracting(ReviewResponse::getProductSlug)
                .containsOnly("brazil-mogiana");
    }

    @Test
    void aPartialProductSlugMatchesNothing() {
        PageResponse<ReviewResponse> page = reviewService.getDashboardReviews(
                "brazil", null, null, null, null, null, null, new BaseFilterRequest());

        assertThat(page.getContent()).isEmpty();
    }

    @Test
    void aDateRangeThatExcludesTheReviewsReturnsNothing() {
        PageResponse<ReviewResponse> page = reviewService.getDashboardReviews(
                null, null, null, null, null,
                java.time.Instant.parse("2000-01-01T00:00:00Z"),
                java.time.Instant.parse("2000-01-02T00:00:00Z"),
                new BaseFilterRequest());

        assertThat(page.getContent()).isEmpty();
    }
}
