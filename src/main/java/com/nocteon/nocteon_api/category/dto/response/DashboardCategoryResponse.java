package com.nocteon.nocteon_api.category.dto.response;

import java.time.Instant;
import java.util.List;

import com.nocteon.nocteon_api.common.dto.TranslationResponse;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class DashboardCategoryResponse {
    private Long id;
    private String slug;
    private String imageUrl;
    private Instant createdAt;
    private Boolean isActive;

    private List<TranslationResponse> translations;

}
