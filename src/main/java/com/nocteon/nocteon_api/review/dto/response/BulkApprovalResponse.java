package com.nocteon.nocteon_api.review.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * How many reviews a bulk approval actually changed — which can be fewer
 * than were asked for, because a review deleted in between is not there any
 * more.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkApprovalResponse {
    private int updated;
}
