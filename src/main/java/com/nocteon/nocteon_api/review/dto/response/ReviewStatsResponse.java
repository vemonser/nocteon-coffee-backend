package com.nocteon.nocteon_api.review.dto.response;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
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