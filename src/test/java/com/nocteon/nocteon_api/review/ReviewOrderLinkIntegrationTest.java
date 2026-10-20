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
 * The order link behind a verified review.
 *
 * <p>Only a review whose purchase is CONFIRMED carries an order — the
 * dashboard shows a small check next to the reviewer's name, and a link to
 * the order, and only when there is an order to link to.
 */
@SpringBootTest(
        classes = NocteonApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ReviewOrderLinkIntegrationTest {

    @Autowired
    private ReviewService reviewService;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    @Test
    void aVerifiedReviewWithAPurchaseCarriesItsOrder() {
        PageResponse<ReviewResponse> page = reviewService.getDashboardReviews(
                null, null, null, null, null, null, null, new BaseFilterRequest());

        assertThat(page.getContent()).isNotEmpty();
        assertThat(page.getContent())
                .filteredOn(r -> Boolean.TRUE.equals(r.isVerified()))
                .isNotEmpty()
                .allSatisfy(r -> {
                    assertThat(r.getOrderId()).isNotNull();
                    assertThat(r.getOrderNumber()).isNotBlank();
                });
    }

    /**
     * An unverified review must carry NO order: the check mark next to the
     * reviewer's name is a promise that the purchase was confirmed, and a
     * link to a random order would make that promise a lie.
     */
    @Test
    void anUnverifiedReviewCarriesNoOrder() {
        PageResponse<ReviewResponse> page = reviewService.getDashboardReviews(
                null, null, null, null, null, null, null, new BaseFilterRequest());

        assertThat(page.getContent())
                .filteredOn(r -> !Boolean.TRUE.equals(r.isVerified()))
                .allSatisfy(r -> {
                    assertThat(r.getOrderId()).isNull();
                    assertThat(r.getOrderNumber()).isNull();
                });
    }
}
