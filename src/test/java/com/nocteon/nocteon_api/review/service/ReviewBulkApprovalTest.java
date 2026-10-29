package com.nocteon.nocteon_api.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nocteon.nocteon_api.review.entity.Review;
import com.nocteon.nocteon_api.review.entity.ReviewStatus;
import com.nocteon.nocteon_api.review.repository.ReviewRepository;

@ExtendWith(MockitoExtension.class)
class ReviewBulkApprovalTest {

    @Mock
    private ReviewRepository reviewRepository;

    @org.mockito.Mock
    private com.nocteon.nocteon_api.audit.service.AuditLogService auditLogService;

    @InjectMocks
    private ReviewService reviewService;

    @Test
    void everyReviewInTheListIsApproved() {
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(Review.builder().status(ReviewStatus.PENDING).build()));
        when(reviewRepository.findById(2L)).thenReturn(Optional.of(Review.builder().status(ReviewStatus.PENDING).build()));
        when(reviewRepository.save(any(Review.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        int updated = reviewService.setApprovalBulk(List.of(1L, 2L), true);

        assertThat(updated).isEqualTo(2);
        ArgumentCaptor<Review> saved = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).allMatch(r -> r.getStatus() == ReviewStatus.APPROVED);
    }

    /**
     * A review that was deleted between the list rendering and the click is
     * not an error — it is simply not there any more. The count must report
     * what actually changed, not what was asked for.
     */
    @Test
    void aReviewThatNoLongerExistsIsSkippedAndNotCounted() {
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(Review.builder().status(ReviewStatus.PENDING).build()));
        when(reviewRepository.findById(99L)).thenReturn(Optional.empty());
        when(reviewRepository.save(any(Review.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        int updated = reviewService.setApprovalBulk(List.of(1L, 99L), true);

        assertThat(updated).isEqualTo(1);
    }

    @Test
    void anEmptyListChangesNothing() {
        int updated = reviewService.setApprovalBulk(List.of(), true);

        assertThat(updated).isZero();
        verify(reviewRepository, never()).save(any(Review.class));
    }
}
