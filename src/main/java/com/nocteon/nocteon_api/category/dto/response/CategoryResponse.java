package com.nocteon.nocteon_api.category.dto.response;

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
}
