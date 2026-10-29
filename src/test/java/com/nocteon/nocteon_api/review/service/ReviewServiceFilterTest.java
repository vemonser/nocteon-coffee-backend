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
import com.nocteon.nocteon_api.review.entity.ReviewStatus;
import com.nocteon.nocteon_api.review.repository.ReviewRepository;

@ExtendWith(MockitoExtension.class)
class ReviewServiceFilterTest {

    @Mock
    private ReviewRepository reviewRepository;

    @org.mockito.Mock
    private com.nocteon.nocteon_api.audit.service.AuditLogService auditLogService;

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
                product.capture(), search.capture(), eq(ReviewStatus.PENDING), eq(Boolean.TRUE), eq(3),
                createdFrom.capture(), createdTo.capture(), any(Pageable.class));

        assertThat(product.getValue()).isEqualTo("brazil-mogiana");
        // The search arrives as a READY-MADE PATTERN: the repository compares
        // it with LOWER(column) LIKE :search, and the pattern is NOT wrapped
        // in LOWER() — that would make PostgreSQL infer the parameter's type
        // as bytea, which has no lower().
        assertThat(search.getValue()).isEqualTo("%excellent%");
        assertThat(createdFrom.getValue()).isEqualTo(from);
        assertThat(createdTo.getValue()).isEqualTo(to);
    }

    /**
     * An absent search must stay null all the way down. The repository query
     * compares it against a value, and the binding guard fails the build on a
     * comparison without a null guard — so a blank string here would become a
     * build failure, not a subtle filter bug.
     *
     * <p>The DATE range is the opposite: an absent bound is a WIDE-OPEN one.
     * A parameter that appears in an "is null" test has no type there, and
     * PostgreSQL then cannot infer it ("could not determine data type of
     * parameter").
     */
    @Test
    void anAbsentSearchStaysNull() {
        when(reviewRepository.findAllDashboard(
                any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        reviewService.getDashboardReviews(
                null, null, null, null, null, null, null, new BaseFilterRequest());

        ArgumentCaptor<Instant> createdFrom = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> createdTo = ArgumentCaptor.forClass(Instant.class);
        verify(reviewRepository).findAllDashboard(
                isNull(), isNull(), isNull(), isNull(), isNull(),
                createdFrom.capture(), createdTo.capture(), any(Pageable.class));

        assertThat(createdFrom.getValue()).isEqualTo(ReviewService.ANY_TIME_FROM);
        assertThat(createdTo.getValue()).isEqualTo(ReviewService.ANY_TIME_TO);
    }
}
