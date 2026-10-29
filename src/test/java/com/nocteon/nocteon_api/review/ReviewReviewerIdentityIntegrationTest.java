package com.nocteon.nocteon_api.review;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.review.dto.response.ReviewResponse;
import com.nocteon.nocteon_api.review.service.ReviewService;
import com.nocteon.nocteon_api.support.TestJwtKeys;

@SpringBootTest(classes = NocteonApiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ReviewReviewerIdentityIntegrationTest {

    @Autowired
    private ReviewService reviewService;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    @Test
    void dashboardReviewExposesReviewerUserIdAndFullName() {
        ReviewResponse response = reviewService.getDashboardById(2L);

        assertThat(response.getUserId()).isEqualTo(2L);
        assertThat(response.getUserFullName()).isEqualTo("Ahmed Ali");
        assertThat(response.getUsername()).isEqualTo("ahmed");
    }
}