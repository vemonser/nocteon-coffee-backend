package com.nocteon.nocteon_api.review.dto.response;

import java.time.Instant;

import com.nocteon.nocteon_api.review.entity.ReviewStatus;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ReviewResponse {
    private Long id;
    private String username;
    private String avatarUrl;
    private Integer rating;
    private String comment;
    private boolean verified;
    private ReviewStatus status;
    private String productName;
    private String productSlug;
    private String primaryImageUrl;
    private Instant createdAt;
    /** The order behind a confirmed purchase, when there is one. */
    private Long orderId;
    private String orderNumber;
}