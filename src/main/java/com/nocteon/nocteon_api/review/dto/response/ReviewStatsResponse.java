package com.nocteon.nocteon_api.review.dto.response;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The moderation statistics for the reviews console.
 *
 * <p>{@code averageRating} counts APPROVED reviews only: a pending review
 * must not move the average the storefront shows.
 *
 * <p>{@code distribution} always carries all five keys, so the dashboard can
 * draw a bar for each star without a null check.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewStatsResponse {
    private long total;
    private long pending;
    private long approved;
    private Double averageRating;
    private Map<String, Long> distribution;
}
