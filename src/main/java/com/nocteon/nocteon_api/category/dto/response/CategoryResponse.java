package com.nocteon.nocteon_api.category.dto.response;

import java.time.Instant;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class CategoryResponse {
    private Long id;
    private String slug;
    private String name;
    private String description;
    private Boolean isActive;
    private String imageUrl;
    private Instant createdAt;
}
