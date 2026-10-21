package com.nocteon.nocteon_api.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nocteon.nocteon_api.review.dto.response.ReviewStatsResponse;
import com.nocteon.nocteon_api.review.repository.ReviewRepository;

@ExtendWith(MockitoExtension.class)
class ReviewStatsServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @InjectMocks
    private ReviewService reviewService;

    @Test
    void theDistributionCoversEveryStarFromOneToFive() {
        when(reviewRepository.count()).thenReturn(15L);
        when(reviewRepository.countByIsApprovedFalse()).thenReturn(2L);
        when(reviewRepository.countByIsApprovedTrue()).thenReturn(13L);
        when(reviewRepository.averageApprovedRating()).thenReturn(4.1);
        when(reviewRepository.countByRating())
                .thenReturn(List.of(new Object[] { 5, 8L }, new Object[] { 4, 4L }, new Object[] { 3, 2L }));

        ReviewStatsResponse stats = reviewService.getReviewStats();

        assertThat(stats.getTotal()).isEqualTo(15L);
        assertThat(stats.getPending()).isEqualTo(2L);
        assertThat(stats.getApproved()).isEqualTo(13L);
        assertThat(stats.getAverageRating()).isEqualTo(4.1);
        // Every bucket is present, even the empty ones: the dashboard draws a
        // bar for each, and a missing key would read as "no data" rather than
        // "nobody gave that rating".
        assertThat(stats.getDistribution()).containsOnlyKeys("1", "2", "3", "4", "5");
        assertThat(stats.getDistribution()).containsEntry("5", 8L).containsEntry("4", 4L)
                .containsEntry("3", 2L).containsEntry("1", 0L).containsEntry("2", 0L);
    }
}
