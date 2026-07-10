package com.nocteon.nocteon_api.review.dto.response;

import java.time.Instant;

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
    private boolean isApproved; 
    private String productSlug; 

    private Instant createdAt;
}