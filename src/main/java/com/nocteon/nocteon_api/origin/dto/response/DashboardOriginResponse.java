package com.nocteon.nocteon_api.origin.dto.response;

import java.time.Instant;
import java.util.List;

import com.nocteon.nocteon_api.common.dto.TranslationResponse;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class DashboardOriginResponse {
    
    private Long id;
    private String slug;
    private String code;
    private Instant createdAt;
    private String imageUrl;

    
    private List<TranslationResponse> translations;
}


 