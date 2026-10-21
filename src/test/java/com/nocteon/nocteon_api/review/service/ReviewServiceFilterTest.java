package com.nocteon.nocteon_api.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.nocteon.nocteon_api.common.dto.BaseFilterRequest;
import com.nocteon.nocteon_api.review.repository.ReviewRepository;

@ExtendWith(MockitoExtension.class)
class ReviewServiceFilterTest {

    @Mock
    private ReviewRepository reviewRepository;

    @InjectMocks
    private ReviewService reviewService;

    @Test
    void theProductSearchAndDateRangeReachTheRepository() {
        when(reviewRepository.findAllDashboard(
                any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        Instant from = Instant.parse("2026-10-01T00:00:00Z");
        Instant to = Instant.parse("2026-10-20T23:59:59Z");
        reviewService.getDashboardReviews(
                "brazil-mogiana", "excellent", Boolean.FALSE, Boolean.TRUE, 3, from, to,
                new BaseFilterRequest());

        ArgumentCaptor<String> product = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> search = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Instant> createdFrom = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> createdTo = ArgumentCaptor.forClass(Instant.class);
        verify(reviewRepository).findAllDashboard(
                product.capture(), search.capture(), eq(Boolean.FALSE), eq(Boolean.TRUE), eq(3),
                createdFrom.capture(), createdTo.capture(), any(Pageable.class));

        assertThat(product.getValue()).isEqualTo("brazil-mogiana");
        assertThat(search.getValue()).isEqualTo("excellent");
        assertThat(createdFrom.getValue()).isEqualTo(from);
        assertThat(createdTo.getValue()).isEqualTo(to);
    }

    /**
     * A null search must stay null all the way down. The repository query
     * compares it against a value, and the binding guard fails the build on a
     * comparison without a null guard — so a blank string here would become a
     * build failure, not a subtle filter bug.
     */
    @Test
    void anAbsentSearchStaysNull() {
        when(reviewRepository.findAllDashboard(
                any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        reviewService.getDashboardReviews(
                null, null, null, null, null, null, null, new BaseFilterRequest());

        verify(reviewRepository).findAllDashboard(
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                any(Pageable.class));
    }
}
