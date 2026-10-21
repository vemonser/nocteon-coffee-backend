package com.nocteon.nocteon_api.review.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One bulk approval for a set of reviews.
 *
 * <p>An empty list is rejected: "approve nothing" is a client bug, and
 * answering it with 200 would hide it.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BulkApprovalRequest {
    @NotEmpty(message = "{validation.reviewIds.notEmpty}")
    private List<Long> ids;
    private boolean approved;
}
