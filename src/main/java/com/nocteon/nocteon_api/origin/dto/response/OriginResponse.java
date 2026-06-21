package com.nocteon.nocteon_api.origin.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class OriginResponse {
    private Long id;
    private String slug;
    private String name;
    private String description;
    private String code;
    private String imageUrl;
    
}

